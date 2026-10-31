#!/usr/bin/env python3
"""Static checks against the user-supplied, unmodified SPW 1.19.0 MS JAR-polyglot."""
import sys,zipfile,hashlib,struct,json
from pathlib import Path
HOST=Path(sys.argv[1]);z=zipfile.ZipFile(HOST)
files={
 'androidx/compose/ui/Է.class':'ce6e126cd845edc847b42ad820fe1746ba8e0b39340332be5343c427b86d576e',
 'androidx/compose/ui/ര.class':'a5c3c8d671e5251bc23c972f5da75b58043e871ffe7502774d42c2f8cd2bb66a'
}
for p,h in files.items():
 assert hashlib.sha256(z.read(p)).hexdigest()==h,(p,'fingerprint mismatch')
assert b'MainSurface.kt' in z.read('androidx/compose/ui/Է.class')
assert b'PlayerScreen.kt' in z.read('androidx/compose/ui/ര.class')
# Verified exact method edge from P3.4; independent read of method call bytes via validated trace parser.
exec(Path(__file__).parent.joinpath('trace.py').read_text(encoding='utf8').split('all_methods=[]')[0].replace('import zipfile,struct,json,sys,collections','import zipfile,struct,json,sys,collections'))
name,methods,_=parse(z.read('androidx/compose/ui/Է.class'))
sign='(Landroidx/compose/ui/ح;ZLandroidx/compose/ui/གྷ;Landroidx/compose/foundation/layout/ColumnScope;Landroidx/compose/runtime/Composer;I)Lkotlin/Unit;'
callee='(Lkotlin/jvm/functions/Function0;ZLandroidx/compose/ui/གྷ;Landroidx/compose/ui/Modifier;Landroidx/compose/runtime/Composer;II)V'
found=[(m,c) for m in methods for c in m['calls'] if m['method']=='Ϳ' and m['descriptor']==sign and c['owner']=='androidx/compose/ui/ര' and c['method']=='Ϳ' and c['descriptor']==callee and c['opcode']=='0xb8']
assert len(found)==1,('expected exactly one call site',len(found))
assert found[0][1]['offset']==119
print(json.dumps({'PASS':True,'host_sha256':hashlib.sha256(HOST.read_bytes()).hexdigest(),'owner_source':'PlayerScreen.kt','caller_source':'MainSurface.kt','invoke':'INVOKESTATIC','offset':119,'matching_sites':1},ensure_ascii=False,indent=2))
