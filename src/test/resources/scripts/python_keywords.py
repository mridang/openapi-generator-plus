import keyword
for kw in sorted(kw.lower() for kw in keyword.kwlist):
    print(kw)
