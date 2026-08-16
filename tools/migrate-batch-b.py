#!/usr/bin/env python3
"""Batch B: split concrete services into service interface (already written) +
service/impl/XxxServiceImpl implementations."""
import os
import re
import subprocess

ROOT = os.getcwd()
SVC = os.path.join(ROOT, "backend/src/main/java/com/myblog/backend/service")

# service name -> nested public static classes to move to the interface
NESTED = {
    "PostService": ["DraftPayload", "RevisionItem"],
    "PublicPostService": ["ResolvedSlug", "PublicPage"],
    "PageViewService": ["DailyCount", "TopPost"],
    "AdminSessionService": ["TokenResult"],
}
SERVICES = [
    "MediaAssetService", "PostService", "PublicPostService", "PageViewService",
    "FeedService", "CategoryTagService", "ProjectService",
    "SiteIntroductionService", "SiteSettingsService", "AdminSessionService",
]


def git(*args):
    subprocess.run(["git", "-C", ROOT] + list(args), check=True)


def strip_nested_class(lines, class_name):
    """Remove a `public static class <name> { ... }` top-level block (brace matched)."""
    out = []
    i = 0
    while i < len(lines):
        line = lines[i]
        if re.search(r"public\s+static\s+class\s+" + class_name + r"\b", line):
            # find opening brace on this or following lines
            j = i
            while "{" not in lines[j]:
                j += 1
            depth = lines[j].count("{") - lines[j].count("}")
            j += 1
            while depth > 0:
                depth += lines[j].count("{") - lines[j].count("}")
                j += 1
            i = j
            continue
        out.append(line)
        i += 1
    return out


def main():
    for name in SERVICES:
        src_rel = os.path.join("backend/src/main/java/com/myblog/backend/service", name + ".java").replace("\\", "/")
        dst_dir = os.path.join(SVC, "impl")
        os.makedirs(dst_dir, exist_ok=True)
        dst = os.path.join(dst_dir, name + "Impl.java")
        # 接口已在工作树就位（service/<name>.java）；实现取自 HEAD 的具体类
        concrete = subprocess.run(
            ["git", "-C", ROOT, "show", "HEAD:" + src_rel],
            capture_output=True, check=True, text=True, encoding="utf-8").stdout
        with open(dst, "w", encoding="utf-8", newline="\n") as f:
            f.write(concrete)

        with open(dst, encoding="utf-8") as f:
            lines = f.read().splitlines(keepends=True)

        # package
        for i, l in enumerate(lines):
            if l.startswith("package "):
                lines[i] = "package com.myblog.backend.service.impl;\n"
                pkg_idx = i
                break

        # imports to add after package line
        imports = ["import com.myblog.backend.service.%s;" % name]
        for nested in NESTED.get(name, []):
            imports.append("import com.myblog.backend.service.%s.%s;" % (name, nested))
        if name == "AdminSessionService":
            imports.append("import com.myblog.backend.service.InvalidExchangeCodeException;")
        existing = {l.strip() for l in lines if l.startswith("import ")}
        block = "".join(imp + "\n" for imp in imports if imp not in existing)
        lines.insert(pkg_idx + 1, block)

        # class declaration
        text = "".join(lines)
        text = re.sub(r"public class %s \{" % name,
                      "public class %sImpl implements %s {" % (name, name), text)

        # strip nested classes (now living on the interface)
        lines = text.splitlines(keepends=True)
        for nested in NESTED.get(name, []):
            lines = strip_nested_class(lines, nested)

        with open(dst, "w", encoding="utf-8", newline="\n") as f:
            f.write("".join(lines))
        git("add", os.path.relpath(dst, ROOT).replace("\\", "/"))
        print("split", name)


if __name__ == "__main__":
    main()
