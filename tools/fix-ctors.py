#!/usr/bin/env python3
"""Fix constructor names in *ServiceImpl files: XxxService( -> XxxServiceImpl(."""
import glob
import os
import re

for path in glob.glob("backend/src/main/java/com/myblog/backend/service/impl/*ServiceImpl.java"):
    name = os.path.basename(path)[:-5]  # XxxServiceImpl
    base = name[:-4]                 # XxxService
    with open(path, encoding="utf-8") as f:
        content = f.read()
    new = re.sub(r"\bpublic\s+" + base + r"\s*\(", "public " + name + "(", content)
    if new != content:
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(new)
        print("fixed", path)
    else:
        print("no change", path)
