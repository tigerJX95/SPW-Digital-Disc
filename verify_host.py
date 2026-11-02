#!/usr/bin/env python3
"""Verify one of the two user-supplied SPW host builds and the actual entry edge."""
import sys,zipfile,hashlib,json
from pathlib import Path
HOST=Path(sys.argv[1]);z=zipfile.ZipFile(HOST)
profiles=json.loads(Path(__file__).with_name('host_builds.json').read_text(encoding='utf8'))
host_sha=hashlib.sha256(HOST.read_bytes()).hexdigest()
profile=next((p for p in profiles if p['host_sha256']==host_sha),None)
assert profile is not None,'Unsupported host build; do not bypass verification'
for key in ['surface','screen']:
 assert hashlib.sha256(z.read(profile[key]+'.class')).hexdigest()==profile[key+'_sha256']
exec(Path(__file__).with_name('trace.py').read_text(encoding='utf8').split('all_methods=[]')[0])
name,methods,_=parse(z.read(profile['surface']+'.class'))
found=[c for m in methods for c in m['calls'] if m['method']=='Ϳ' and m['descriptor']==profile['caller_descriptor'] and c['owner']==profile['screen'] and c['method']=='Ϳ' and c['descriptor']==profile['callee_descriptor'] and c['opcode']=='0xb8']
assert len(found)==1 and found[0]['offset']==119,found
print(json.dumps({'PASS':True,'profile':profile['version'],'host_sha256':host_sha,'invoke':'INVOKESTATIC','offset':119,'matching_sites':1},ensure_ascii=False,indent=2))
