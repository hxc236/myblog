#!/usr/bin/env python3
"""Batch A: move production code com.myblog.publicsite -> com.myblog.backend layout,
rename PublicSiteApplication -> BackendApplication, rewrite packages/imports."""
import os
import re
import subprocess
import sys

ROOT = os.getcwd()
MAIN = os.path.join(ROOT, "backend/src/main/java/com/myblog")
TEST = os.path.join(ROOT, "backend/src/test/java/com/myblog")

# filename -> (target subpackage, optional rename)
FILE_MAP = {
    # root
    "PublicSiteApplication.java": ("", "BackendApplication.java"),
    # config
    "AdminSecurityConfig.java": ("config", None),
    "AdminOAuthSuccessHandler.java": ("config", None),
    "AdminTokenAuthenticationFilter.java": ("config", None),
    "DatabaseUrlEnvironmentPostProcessor.java": ("config", None),
    "SiteDataSourceCondition.java": ("config", None),
    "SiteDataSourceConfig.java": ("config", None),
    "MediaStorageConfig.java": ("config", None),
    "PublicCorsConfig.java": ("config", None),
    # utils
    "TokenUtil.java": ("utils", None),
    "MediaContentValidator.java": ("utils", None),
    "ContentLoader.java": ("utils", None),
    "MvpContentImporter.java": ("utils", None),
    # service
    "AdminSessionService.java": ("service", None),
    "PageViewService.java": ("service", None),
    "FeedService.java": ("service", None),
    "MediaAssetService.java": ("service", None),
    "PostService.java": ("service", None),
    "PublicPostService.java": ("service", None),
    "CategoryTagService.java": ("service", None),
    "ProjectService.java": ("service", None),
    "SiteIntroductionService.java": ("service", None),
    "SiteSettingsService.java": ("service", None),
    "MediaStorage.java": ("service", None),
    "InvalidExchangeCodeException.java": ("service", None),
    # service/impl (adapters)
    "LocalMediaStorage.java": ("service/impl", None),
    "S3MediaStorage.java": ("service/impl", None),
    # pojo
    "AdminPrincipal.java": ("pojo", None),
    "Introduction.java": ("pojo", None),
    "Post.java": ("pojo", None),
    "PostMeta.java": ("pojo", None),
    "Project.java": ("pojo", None),
    "SkillGroup.java": ("pojo", None),
    "MediaAsset.java": ("pojo", None),
    "AdminPostDetail.java": ("pojo", None),
    "AdminPostSummary.java": ("pojo", None),
    "PublicPostDetail.java": ("pojo", None),
    "PublicPostSummary.java": ("pojo", None),
    "CategoryItem.java": ("pojo", None),
    "ProjectItem.java": ("pojo", None),
    "SiteIntroduction.java": ("pojo", None),
    "SiteSettings.java": ("pojo", None),
    "TagItem.java": ("pojo", None),
    # controller (all of web/)
}

WEB_CONTROLLERS = [
    "AdminAnalyticsController.java", "AdminAuthController.java", "AdminImportController.java",
    "AdminMediaController.java", "AdminPostController.java", "AdminProjectController.java",
    "AdminSearchIndexController.java", "AdminSiteSettingsController.java",
    "AdminTaxonomyController.java", "ApiExceptionHandler.java", "FeedController.java",
    "MediaReadController.java", "PageViewController.java", "ProjectsApiController.java",
    "PublicPostsController.java", "SiteApiController.java", "TaxonomyApiController.java",
]
for name in WEB_CONTROLLERS:
    FILE_MAP[name] = ("controller", None)

# class name -> new fully qualified package (relative to com.myblog.backend)
TYPE_PKG = {name: sub for name, (sub, _) in FILE_MAP.items() if name.endswith(".java")}
TYPE_PKG = {name[:-5]: sub for name, sub in TYPE_PKG.items()}

PKG_RE = re.compile(r"^package com\.myblog\.publicsite(\.[\w.]+)?;")
IMPORT_RE = re.compile(r"^import (static )?com\.myblog\.publicsite(?:\.(\w+))?\.([\w.*]+);")
FQ_RE = re.compile(r"com\.myblog\.publicsite(?:\.(\w+))?\.(\w+)")


def map_import(rest):
    """Map `com.myblog.publicsite[.<sub>].<Type>` -> `com.myblog.backend[.<sub>].<Type>`."""
    sub, name = rest
    if name in TYPE_PKG:
        pkg = TYPE_PKG[name]
        return ("com.myblog.backend." + pkg) if pkg else "com.myblog.backend", name
    return None


def rewrite_main_content(content, new_pkg):
    lines = []
    for line in content.splitlines():
        m = PKG_RE.match(line)
        if m:
            pkg = ("com.myblog.backend." + new_pkg) if new_pkg else "com.myblog.backend"
            lines.append("package %s;" % pkg)
            continue
        m = IMPORT_RE.match(line)
        if m:
            mapped = map_import((m.group(2), m.group(3)))
            if mapped:
                pkg, name = mapped
                lines.append("import %s%s.%s;" % (m.group(1) or "", pkg, name))
                continue
            raise SystemExit("unmapped import: " + line)
        lines.append(line)
    text = "\n".join(lines)
    text = FQ_RE.sub(
        lambda mm: (lambda mapped: "%s.%s" % (mapped[0], mapped[1]) if mapped else mm.group(0))(
            map_import((mm.group(1), mm.group(2)))), text)
    text = text.replace("PublicSiteApplication", "BackendApplication")
    return text


def git(*args):
    subprocess.run(["git", "-C", ROOT] + list(args), check=True)


def main():
    # 1. delete legacy tree first (path collision with new BackendApplication.java)
    legacy = os.path.join(MAIN, "backend")
    git("rm", "-r", "-q", os.path.relpath(legacy, ROOT).replace("\\", "/"))

    # 2. move production files
    old_root = os.path.join(MAIN, "publicsite")
    for dirpath, dirnames, filenames in os.walk(old_root):
        dirnames.sort()
        for fname in sorted(filenames):
            if not fname.endswith(".java"):
                continue
            rel = os.path.relpath(os.path.join(dirpath, fname), old_root).replace("\\", "/")
            if fname not in FILE_MAP:
                raise SystemExit("unmapped file: " + rel)
            sub, rename = FILE_MAP[fname]
            target_name = rename or fname
            target_dir = os.path.join(MAIN, "backend", *sub.split("/")) if sub else os.path.join(MAIN, "backend")
            os.makedirs(target_dir, exist_ok=True)
            src = os.path.join(dirpath, fname)
            dst = os.path.join(target_dir, target_name)
            git("mv", os.path.relpath(src, ROOT).replace("\\", "/"),
                os.path.relpath(dst, ROOT).replace("\\", "/"))
            with open(dst, encoding="utf-8") as f:
                content = f.read()
            with open(dst, "w", encoding="utf-8", newline="\n") as f:
                f.write(rewrite_main_content(content, sub))

    # 3. move tests
    old_test_root = os.path.join(TEST, "publicsite")
    for dirpath, dirnames, filenames in os.walk(old_test_root):
        dirnames.sort()
        for fname in sorted(filenames):
            if not fname.endswith(".java"):
                continue
            rel = os.path.relpath(os.path.join(dirpath, fname), old_test_root).replace("\\", "/")
            sub = os.path.dirname(rel)
            target_dir = os.path.join(TEST, "backend", *sub.split("/")) if sub else os.path.join(TEST, "backend")
            os.makedirs(target_dir, exist_ok=True)
            src = os.path.join(dirpath, fname)
            dst = os.path.join(target_dir, fname)
            git("mv", os.path.relpath(src, ROOT).replace("\\", "/"),
                os.path.relpath(dst, ROOT).replace("\\", "/"))
            with open(dst, encoding="utf-8") as f:
                content = f.read()
            with open(dst, "w", encoding="utf-8", newline="\n") as f:
                f.write(rewrite_main_content(content, sub))

    # 4. spring.factories
    factories = os.path.join(ROOT, "backend/src/main/resources/META-INF/spring.factories")
    with open(factories, encoding="utf-8") as f:
        content = f.read()
    with open(factories, "w", encoding="utf-8", newline="\n") as f:
        f.write(content.replace(
            "com.myblog.publicsite.config.DatabaseUrlEnvironmentPostProcessor",
            "com.myblog.backend.config.DatabaseUrlEnvironmentPostProcessor"))

    print("Batch A move done")


if __name__ == "__main__":
    main()
