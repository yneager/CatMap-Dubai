"""Derive TS interfaces, defaults, enum labels, colors and parity schema from Kotlin.
No Android file is modified. Run from web/ or repository root.
"""
import json, re
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'app/src/main/java/com/catmap/dubai'
DEST = ROOT / 'web/src/data/model'
DEST.mkdir(parents=True, exist_ok=True)
schema = {}
out = ['// Generated from Android Kotlin models. Run npm run models:generate.', "import type { Timestamp } from 'firebase/firestore';"]
for path in sorted((SOURCE / 'data/model').glob('*.kt')):
    text = re.sub(r'/\*.*?\*/|//[^\n]*', '', path.read_text(encoding='utf-8'), flags=re.S)
    for match in re.finditer(r'data class (\w+)\(\s*\n(.*?)\n\)', text, re.S):
        name, body = match.groups()
        fields = []
        for line in body.splitlines():
            field = re.search(r'(?:(@DocumentId)\s+)?var (\w+):\s*(.*?)\s*=\s*(.*?),?$', line.strip())
            if not field:
                continue
            annotation, key, kind, default = field.groups()
            default = default.rstrip(',')
            if default.endswith('.key'):
                default = json.dumps(default.split('.')[-2].lower())
            elif default.startswith('Roles.'):
                default = json.dumps(default.split('.')[-1].lower())
            elif default == 'emptyMap()': default = '{}'
            elif default == 'emptyList()': default = '[]'
            else: default = re.sub(r'(\d)L$', r'\1', default)
            ts = {'String':'string','Long':'number','Int':'number','Double':'number','Boolean':'boolean','Timestamp?':'Timestamp | null'}.get(kind, kind)
            ts = re.sub(r'List<(\w+)>', lambda m: {'String':'string','Double':'number'}.get(m[1],m[1])+'[]', ts)
            ts = re.sub(r'Map<String, (\w+)>', lambda m:'Record<string, '+{'String':'string','Long':'number'}.get(m[1],m[1])+'>',ts)
            fields.append((key,kind,ts,default,bool(annotation)))
        schema[name] = {key:{'type':kind,'documentId':identity} for key,kind,_,_,identity in fields}
        out += [f'export interface {name} {{'] + [f'  {key}: {ts};' for key,_,ts,_,_ in fields] + ['}']
        out += [f'export const {name}Defaults: {name} = {{'] + [f'  {key}: {default},' for key,_,_,default,_ in fields] + ['};']
        identity = next((key for key,_,_,_,isid in fields if isid), None)
        out += [f'export function {name}FromDoc(id: string, data: Record<string, unknown>): {name} {{', f'  return Object.assign(Object.fromEntries(Object.entries({name}Defaults).map(([key, value]) => [key, key in data ? data[key] : value]))'+(f', {{ {identity}: id }}' if identity else '')+f') as unknown as {name};', '}']
        # Restrict every serialization to Kotlin fields, omit @DocumentId, round all integer values.
        out += [f'export function {name}ToDoc(value: {name}): Record<string, unknown> {{','  return {']
        for key,kind,_,_,isid in fields:
            if isid: continue
            expression = f'value.{key}'
            if kind in ('Long','Int'): expression = f'Math.round({expression})'
            if kind == 'Map<String, Long>': expression = f'Object.fromEntries(Object.entries({expression}).map(([k,v])=>[k,Math.round(v)]))'
            out.append(f'    {key}: {expression},')
        out += ['  };','}']
(DEST / 'index.ts').write_text('\n'.join(out)+'\n',encoding='utf-8')
(DEST / 'kotlin-schema.json').write_text(json.dumps(schema,indent=2)+'\n',encoding='utf-8')
enums = {}
for path in sorted((SOURCE / 'data/model').glob('*.kt')):
    text=path.read_text(encoding='utf-8')
    for match in re.finditer(r'enum class (\w+)\(.*?\) : Labeled \{(.*?)override val key',text,re.S):
        name,body=match.groups()
        values=[]
        for item in re.finditer(r'^\s*(\w+)\((R\.string\.(\w+).*?)\)[,;]',body,re.M):
            key, args, label=item.groups()
            colors=re.findall(r'0xFF([0-9A-Fa-f]{6})',args)
            values.append({'key':key.lower(),'label':label,**({'color':'#'+colors[0]} if colors else {}),**({'colors':['#'+color for color in colors]} if name=='CoatColor' and colors else {})})
        enums[name]=values
(DEST/'Enums.ts').write_text('// Generated from Kotlin enums.\nexport const enums = '+json.dumps(enums,indent=2)+' as const;\n',encoding='utf-8')
# Geography includes the existing Arabic names verbatim.
hoods=[dict(zip(('key','nameEn','nameAr','lat','lng'),(a,b,c,float(d),float(e)))) for a,b,c,d,e in re.findall(r'Neighborhood\("(.*?)", "(.*?)", "(.*?)", ([\d.]+), ([\d.]+)\)',(SOURCE/'data/Neighborhoods.kt').read_text(encoding='utf-8'))]
(ROOT/'web/src/data/neighborhoods.json').write_text(json.dumps(hoods,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(f'Generated {len(schema)} models and {len(enums)} enums from Kotlin')
