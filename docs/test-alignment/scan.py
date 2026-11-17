import re,os,sys,glob,json,collections
R=__import__('os').path.abspath(__import__('os').path.join(__import__('os').path.dirname(__import__('os').path.abspath(__file__)),'..','..'))
G=R+'/src/spec/resources/generated'
TOK=re.compile(r'bhv([a-z]+)(\d{3})(?!\d)')
def ident(name):
    n=re.sub(r'[^a-z0-9]','',name.lower()); m=TOK.search(n); return m.group(1)+m.group(2) if m else None
def files(l,pats):
    out=[]
    for p in pats: out+=glob.glob(f'{G}/{l}/{p}',recursive=True)
    return sorted(set(out))
SPEC={
 'java':(['src/test/**/*.java'],[r'void\s+(\w+)\s*\(']),
 'kotlin':(['src/test/**/*.kt'],[r'@DisplayName\("([^"]+)"\)',r'fun\s+`([^`]+)`',r'fun\s+(\w+)\s*\(']),
 'csharp':(['Test/**/*.cs'],[r'public\s+(?:async\s+)?(?:Task|void)\s+(\w+)\s*\(']),
 'php':(['tests/**/*.php'],[r"(?:test|it)\(\s*'((?:[^'\\]|\\.)*)'",r'(?:test|it)\(\s*"((?:[^"\\]|\\.)*)"']),
 'python':(['test/**/*.py'],[r'def\s+(test_\w+)']),
 'ruby':(['test/**/*.rb','spec/**/*.rb'],[r"\bit\s+'((?:[^'\\]|\\.)*)'",r'\bit\s+"((?:[^"\\]|\\.)*)"',r'def\s+(test_\w+)']),
 'node':(['test/**/*.ts'],[r"\b(?:it|test)\(\s*'((?:[^'\\]|\\.)*)'",r'\b(?:it|test)\(\s*"((?:[^"\\]|\\.)*)"',r'\b(?:it|test)\(\s*`((?:[^`\\]|\\.)*)`']),
 'dart':(['test/**/*.dart'],[r"\b(?:test|testWidgets)\(\s*'((?:[^'\\]|\\.)*)'",r'\b(?:test)\(\s*"((?:[^"\\]|\\.)*)"']),
 'go':(['**/*_test.go'],[r'func\s+(Test\w+)\s*\(']),
 'rust':(['tests/**/*.rs','src/**/*.rs'],[r'fn\s+(test_\w+)\s*\(']),
 'swift':(['Tests/**/*.swift'],[r'func\s+(test\w+)\s*\(']),
 'elixir':(['test/**/*.exs'],[r'\btest\s+"((?:[^"\\]|\\.)*)"']),
}
def names(l):
    pats,rx=SPEC[l]; out=[]
    for f in files(l,pats):
        txt=open(f,errors='replace').read()
        if l=='rust':
            # only fns following #[test]/#[tokio::test]
            for m in re.finditer(r'#\[(?:tokio::)?test[^\]]*\]\s*(?:async\s+)?fn\s+(\w+)',txt): out.append((f,m.group(1)))
            continue
        if l=='kotlin':
            # one name per test: DisplayName if present directly above, else fun name
            for m in re.finditer(r'@Test\b(?:[^\n]*\n)+?\s*(?:@DisplayName\("([^"]+)"\)\s*)?(?:suspend\s+)?fun\s+(?:`([^`]+)`|(\w+))',txt):
                out.append((f,m.group(1) or m.group(2) or m.group(3)))
            continue
        for r in rx:
            for m in re.finditer(r,txt): out.append((f,m.group(1)))
    return out
def catalogue():
    ids={};multi=set()
    for l in open(R+'/src/spec/resources/test-catalogue.txt'):
        l=l.strip()
        if not l or l.startswith('#'): continue
        p=[x.strip() for x in l.split('|')]
        ids[p[0]]=p[1]
        if len(p)>2 and p[2]=='multi': multi.add(p[0])
    return ids,multi
def exceptions():
    ex=collections.defaultdict(dict)
    for l in open(R+'/src/spec/resources/test-catalogue-exceptions.txt'):
        l=l.strip()
        if not l or l.startswith('#'): continue
        p=[x.strip() for x in l.split('|')]
        ex[p[0]][p[1]]=p[2] if len(p)>2 else ''
    return ex
if __name__=='__main__':
    ids,multi=catalogue(); ex=exceptions(); res={}
    for l in SPEC:
        ns=names(l); by=collections.defaultdict(list); un=0
        for f,n in ns:
            i=ident(n)
            if i: by[i].append((os.path.relpath(f,G+'/'+l),n))
            else: un+=1
        missing=[i for i in ids if i not in by and i not in ex[l]]
        dups={i:v for i,v in by.items() if i in ids and len(v)>1 and i not in multi}
        unknown=[i for i in by if i not in ids]
        stale=[i for i in ex[l] if i in by and i!='*unnumbered']
        res[l]=dict(total=len(ns),unnumbered=un,missing=missing,dups={i:[n for f,n in v] for i,v in dups.items()},unknown=unknown,stale=stale)
        print(f"{l:8} tests={len(ns):5} unnumbered={un:4} missing={len(missing):3} dups={len(dups):3} unknown={len(unknown)} stale={len(stale)}")
    pass
