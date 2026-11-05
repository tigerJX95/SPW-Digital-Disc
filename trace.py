import zipfile,struct,json,sys,collections
target_words=('PlayerScreen.kt','PlayerLyrics.kt')
def parse(data):
 p=8; n=struct.unpack_from('>H',data,p)[0];p+=2; cp=[None]*n;i=1
 while i<n:
  tag=data[p];p+=1
  if tag==1:
   size=struct.unpack_from('>H',data,p)[0];p+=2;cp[i]=('utf',data[p:p+size].decode('utf-8','replace'));p+=size
  elif tag in (7,8,16,19,20):cp[i]=(tag,struct.unpack_from('>H',data,p)[0]);p+=2
  elif tag in (9,10,11,12,17,18):cp[i]=(tag,*struct.unpack_from('>HH',data,p));p+=4
  elif tag in (3,4):p+=4
  elif tag in (5,6):p+=8;i+=1
  elif tag==15:p+=3
  else:raise ValueError(tag)
  i+=1
 def utf(k):return cp[k][1]
 def cls(k):return utf(cp[k][1])
 p+=2;name=cls(struct.unpack_from('>H',data,p)[0]);p+=4
 iface=struct.unpack_from('>H',data,p)[0];p+=2+iface*2
 field_count=struct.unpack_from('>H',data,p)[0];p+=2
 for _ in range(field_count):
  p+=6
  ac=struct.unpack_from('>H',data,p)[0];p+=2
  for j in range(ac):
   p+=2;sz=struct.unpack_from('>I',data,p)[0];p+=4+sz
 method_count=struct.unpack_from('>H',data,p)[0];p+=2; methods=[]
 for _ in range(method_count):
  access,method,desc,attrs=struct.unpack_from('>HHHH',data,p);p+=8;calls=[]
  for j in range(attrs):
   ix=struct.unpack_from('>H',data,p)[0];p+=2; sz=struct.unpack_from('>I',data,p)[0];p+=4
   if utf(ix)=='Code':
    code_len=struct.unpack_from('>I',data,p+4)[0];code=data[p+8:p+8+code_len];calls=scan(code,cp,cls,utf)
   p+=sz
  methods.append({'method':utf(method),'descriptor':utf(desc),'calls':calls})
 return name,methods,any(x[0]=='utf' and any(w in x[1] for w in target_words) for x in cp if x)
def scan(b,cp,cls,utf):
 out=[];pos=0;length=len(b)
 wide2={0x10:2,0x12:2,0x15:2,0x16:2,0x17:2,0x18:2,0x19:2,0x36:2,0x37:2,0x38:2,0x39:2,0x3a:2,0xa9:2,0xbc:2}
 wide3=set([0x11,0x13,0x14,0x84,0x99,0x9a,0x9b,0x9c,0x9d,0x9e,0x9f,0xa0,0xa1,0xa2,0xa3,0xa4,0xa5,0xa6,0xa7,0xa8,0xc6,0xc7])|set(range(0xb2,0xb9))|{0xbb,0xbd,0xc0,0xc1}
 while pos<length:
  op=b[pos]
  if op in (0xb6,0xb7,0xb8,0xb9,0xba):
   idx=int.from_bytes(b[pos+1:pos+3],'big'); c=cp[idx]
   if c and c[0] in (10,11):
    n=cp[c[2]];out.append({'offset':pos,'opcode':hex(op),'owner':cls(c[1]),'method':utf(n[1]),'descriptor':utf(n[2])})
  if op==0xaa:
   k=(pos+4)&~3;lo=int.from_bytes(b[k+4:k+8],'big',signed=True);hi=int.from_bytes(b[k+8:k+12],'big',signed=True);step=k+12+4*(hi-lo+1)-pos
  elif op==0xab:
   k=(pos+4)&~3;cnt=int.from_bytes(b[k+4:k+8],'big');step=k+8+8*cnt-pos
  elif op==0xc4:step=6 if b[pos+1]==0x84 else 4
  elif op in (0xb9,0xba,0xc8,0xc9):step=5
  elif op==0xc5:step=4
  else:step=wide2.get(op,3 if op in wide3 else 1)
  pos+=step
 return out
def main():
 with zipfile.ZipFile(sys.argv[1]) as z:
  all_methods=[];targets=[];bad=0
  for entry in z.infolist():
   if not entry.filename.endswith('.class'):continue
   try:
    name,methods,marker=parse(z.read(entry));all_methods.extend((name,entry.filename,m) for m in methods)
    if marker:targets.append({'entry':entry.filename,'internal_name':name,'methods':[{'name':m['method'],'desc':m['descriptor']} for m in methods if 'Landroidx/compose/runtime/Composer;' in m['descriptor']]})
   except Exception as e:
    bad+=1
    if bad<5:print(entry.filename,repr(e),file=sys.stderr)
  owners={t['internal_name'] for t in targets};incoming=[]
  for name,path,m in all_methods:
   for c in m['calls']:
    if c['owner'] in owners:incoming.append({'caller_class':name,'caller_entry':path,'caller_method':m['method'],'caller_descriptor':m['descriptor'],**c})
  result={'class_count':sum(x.filename.endswith('.class') for x in z.infolist()),'parse_errors':bad,'source_marker_classes':targets,'actual_invoke_edges':incoming}
  print(json.dumps(result,ensure_ascii=False,indent=2))

if __name__ == '__main__':
 main()
