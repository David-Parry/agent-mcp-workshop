#!/usr/bin/env python3
"""Walk the workshop the way students do, from trunk through chapter 07.

Nothing here reads from or writes to origin. The walk snapshots this working
tree (uncommitted and untracked files included, ignored ones not) into a
throwaway bare repository, publishes the chapter branches there as one
chain -- trunk, 01-chapter, the 01 solution, 02-chapter, ... the 07
solution -- and follows every handout from two clones of it:

* the checkout student starts each chapter with ./clean-checkout.sh, and has
  to finish it on exactly that chapter's solution;
* the merge student never checks out: it commits each chapter and merges the
  next branch, and has to finish chapter 07 with complete's code.

Usage (from a checkout of complete):
    python3 tools/walkthrough.py all                  # snapshot, chain, walk
    python3 tools/walkthrough.py walk --through 03    # walk the last chain again
"""

from __future__ import annotations

import argparse
import calendar
import json
import os
import re
import shlex
import shutil
import subprocess
import sys
import textwrap
import time
import xml.etree.ElementTree as ET
from dataclasses import asdict, dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
import slice_chapter as slicer  # noqa: E402

WORK = Path(os.environ.get("AMW_WALK_DIR", "/tmp/amw-walk"))
SOURCE = WORK / "source"
ORIGIN = WORK / "origin.git"
SLICES = WORK / "slices"
STUDENTS = WORK / "students"
LOGS = WORK / "logs"
CHAPTERS = [f"{n:02d}" for n in range(1, 8)]

# A student who follows every handout ends chapter 07 with these, byte for
# byte as complete has them. Lessons, decks and docs are not compared.
EXACT = [
    "src", "build.gradle", "settings.gradle", "gradle", "gradlew", "gradlew.bat",
    "inspector", slicer.HARNESS, "clean-checkout.sh", "verification.sh",
    ".gitignore", "LICENSE",
]

# Commands a student leaves running in a window of their own. The walk lists
# them for a person to try instead of running them, and assumes the student
# carries on in a new terminal at the top of the repository.
INTERACTIVE = re.compile(r"^\s*(?:\./run\.sh|\./inspector/run\.sh|npx\b|open\b|claude\b|cursor\b|code\b)")
SHELL = {"bash", "sh", "shell", "zsh", "console"}
PLACEHOLDER = re.compile(r"^(\s*)// Chapter (\d\d): ")
CASE_LINE = re.compile(r"^(\s*)case (\w+) -> \{\s*$")
DECLARATION = re.compile(
    r"^\s*(?:(?:public|private|protected|static|final|synchronized|default)\s+)+"
    r"[\w.$<>\[\], ?]+\s+(\w+)\s*\("
)
PASTE_CUE = re.compile(r"\b(?:paste|replace it with|fill)\b", re.I)
GRADING_HEADING = re.compile(r"done when|testing|verif", re.I)
COUNT_SENTENCE = re.compile(r"reports (\d+) tests?, and (\d+) of them fail before you start")
HINT_ITEM = re.compile(r"^\s*[-*]\s+(`.*)$")
TEST_NAME = re.compile(r"^[a-z][A-Za-z0-9_]*(?:\*|_?\.\.\.)?$")
BRANCH_TOKEN = re.compile(r"\b((?:0[1-7]|agent)-chapter)\b|`(complete|trunk)`")
LINK = re.compile(r"\]\(([^)\s#]+)(?:#[^)]*)?\)")
PATH_TOKEN = re.compile(r"`((?:lessons|src|inspector|tools)/[^`\s]*(?:\.\w+|/))`")
# Trunk proves a machine is ready for the chapters, so it has to run the
# same wrapper and scripts they do, and hand out the same setup docs.
TOOLCHAIN = [
    "gradle/wrapper/gradle-wrapper.properties", "gradle/wrapper/gradle-wrapper.jar", "gradlew",
    "gradlew.bat", "settings.gradle", "verification.sh", "clean-checkout.sh", ".gitignore", "LICENSE",
    "00-setup.md", "00-introduction.md",
]
EPOCH = calendar.timegm((2026, 7, 28, 0, 0, 0))
TIMEOUT = 1200


@dataclass
class Finding:
    stage: str
    student: str
    kind: str
    detail: str


FINDINGS: list[Finding] = []
SUMMARY: list[str] = []
MANUAL: list[str] = []


def finding(stage, student, kind, detail):
    FINDINGS.append(Finding(stage, student, kind, detail))
    first = detail.strip().splitlines()[0] if detail.strip() else ""
    print(f"   !! {stage} {student}: {kind}: {first}")


def note(line):
    SUMMARY.append(line)
    print(f"   {line}")


# --- processes and git ------------------------------------------------------

def clean_env(**extra):
    env = {k: v for k, v in os.environ.items() if not k.startswith("GIT_")}
    env.update(extra)
    return env


def run(args, cwd=None, env=None, check=True, timeout=None, log=None):
    try:
        proc = subprocess.run(args, cwd=cwd, env=env or clean_env(), stdin=subprocess.DEVNULL,
                              stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
                              timeout=timeout)
        code, out = proc.returncode, proc.stdout
    except subprocess.TimeoutExpired as expired:
        partial = expired.stdout or ""
        out = partial.decode(errors="replace") if isinstance(partial, bytes) else partial
        code, out = 124, out + f"\n[walkthrough] timed out after {timeout}s\n"
    if log:
        log.parent.mkdir(parents=True, exist_ok=True)
        log.write_text(out)
    if check and code:
        raise SystemExit(f"{' '.join(map(str, args))} failed ({code}) in {cwd}:\n{out[-3000:]}")
    return code, out


def origin_git(*args, work_tree=None, index=None, extra=None):
    env = clean_env(GIT_DIR=str(ORIGIN))
    if work_tree:
        env["GIT_WORK_TREE"] = str(work_tree)
    if index:
        env["GIT_INDEX_FILE"] = str(index)
    env.update(extra or {})
    return run(["git", *args], cwd=work_tree or WORK, env=env)[1].strip()


def write_tree(directory):
    index = WORK / "walk.index"
    index.unlink(missing_ok=True)
    origin_git("add", "-A", ".", work_tree=directory, index=index)
    tree = origin_git("write-tree", work_tree=directory, index=index)
    index.unlink()
    return tree


def commit_tree(tree, parents, message, when):
    name = run(["git", "config", "user.name"], cwd=ROOT, check=False)[1].strip() or "Workshop"
    email = run(["git", "config", "user.email"], cwd=ROOT, check=False)[1].strip() or "workshop@example.com"
    stamp = f"@{when} +0000"
    identity = dict(GIT_AUTHOR_NAME=name, GIT_AUTHOR_EMAIL=email, GIT_AUTHOR_DATE=stamp,
                    GIT_COMMITTER_NAME=name, GIT_COMMITTER_EMAIL=email, GIT_COMMITTER_DATE=stamp)
    args = ["commit-tree", tree, "-m", message]
    for parent in parents:
        args += ["-p", parent]
    return origin_git(*args, extra=identity)


def reset(path):
    if path.exists():
        shutil.rmtree(path)
    path.mkdir(parents=True)


# --- snapshot and chain -----------------------------------------------------

def snapshot():
    print("== snapshot")
    branch = run(["git", "rev-parse", "--abbrev-ref", "HEAD"], cwd=ROOT)[1].strip()
    if branch != "complete":
        raise SystemExit(f"walk from a checkout of complete, not {branch}")
    listed = run(["git", "ls-files", "-co", "--exclude-standard", "-z"], cwd=ROOT)[1]
    files = [rel for rel in listed.split("\0") if rel and os.path.lexists(ROOT / rel)]
    reset(SOURCE)
    for rel in files:
        (SOURCE / rel).parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(ROOT / rel, SOURCE / rel, follow_symlinks=False)
    if ORIGIN.exists():
        shutil.rmtree(ORIGIN)
    run(["git", "init", "-q", "--bare", str(ORIGIN)])
    origin_git("fetch", "-q", str(ROOT), "+refs/heads/trunk:refs/heads/trunk", "+HEAD:refs/walk/complete-head")
    head = origin_git("rev-parse", "refs/walk/complete-head")
    snap = commit_tree(write_tree(SOURCE), [head], "Snapshot complete's working tree.", EPOCH)
    origin_git("update-ref", "refs/heads/complete", snap)
    origin_git("symbolic-ref", "HEAD", "refs/heads/trunk")
    dirty = len(run(["git", "status", "--porcelain"], cwd=ROOT)[1].splitlines())
    note(f"snapshot: {len(files)} files from complete at {head[:7]}, {dirty} uncommitted change(s)")


def slice_copy(chapter, filled):
    dest = SLICES / (chapter + ("-filled" if filled else ""))
    shutil.copytree(SOURCE, dest)
    run(["git", "init", "-q"], cwd=dest)
    run(["git", "add", "-A"], cwd=dest)
    args = [sys.executable, "tools/slice_chapter.py", chapter] + (["--keep-filled"] if filled else [])
    code, out = run(args, cwd=dest, check=False, log=LOGS / f"slice-{dest.name}.log")
    if code:
        problems = [line.strip() for line in out.splitlines() if "!!" in line]
        finding(chapter, "chain", "slicer failed", "\n".join(problems) or out[-1500:])
    shutil.rmtree(dest / ".git")
    for cache in list(dest.rglob("__pycache__")):
        shutil.rmtree(cache)
    return dest


def hollowed_files(chapter):
    spec = slicer.HOLLOW[chapter]
    files = {str(path.relative_to(slicer.ROOT)) for path, _ in spec.get("methods", [])}
    if spec.get("cases") or spec.get("prelude") or spec.get("notification_switch"):
        files.add("src/main/java/com/workshop/mcp/IORouter.java")
    return files


def chain():
    print("== chain")
    reset(SLICES)
    parent = origin_git("rev-parse", "refs/heads/trunk")
    when = EPOCH
    for chapter in CHAPTERS:
        title = slicer.CHAPTER_TITLES[chapter]
        start_tree = write_tree(slice_copy(chapter, filled=False))
        solved_tree = write_tree(slice_copy(chapter, filled=True))
        when += 60
        start = commit_tree(start_tree, [parent], f"Start chapter {chapter}: {title}.", when)
        when += 60
        solved = commit_tree(solved_tree, [start], f"Solve chapter {chapter}: {title}.", when)
        origin_git("update-ref", f"refs/heads/{chapter}-chapter", start)
        origin_git("update-ref", f"refs/walk/{chapter}-solution", solved)
        changed = origin_git("diff", "--name-only", start, solved).splitlines()
        unexpected = sorted(set(changed) - hollowed_files(chapter))
        if unexpected:
            finding(chapter, "chain", "solution touches files the chapter does not hollow", "\n".join(unexpected))
        note(f"{chapter}-chapter {start[:7]}, solution {solved[:7]}: {len(changed)} file(s) to fill")
        parent = solved


# --- reading a handout ------------------------------------------------------

@dataclass
class Block:
    kind: str           # heading, prose or fence
    text: list
    line: int           # handout line the block starts on
    lang: str = ""
    stretch: bool = False
    grading: bool = False


def parse_handout(text):
    blocks, prose = [], []
    lines = text.splitlines()
    stretch = grading = None
    prose_start = 0

    def flush():
        if prose:
            blocks.append(Block("prose", prose[:], prose_start,
                                stretch=stretch is not None, grading=grading is not None))
            prose.clear()

    i = 0
    while i < len(lines):
        line = lines[i]
        fence = re.match(r"^(\s*)```\s*([\w+-]*)\s*$", line)
        heading = re.match(r"^(#{1,6})\s+(.*)$", line)
        if fence:
            flush()
            indent = len(fence.group(1))
            body, j = [], i + 1
            while j < len(lines) and not re.match(r"^\s*```\s*$", lines[j]):
                body.append(lines[j][indent:] if not lines[j][:indent].strip() else lines[j])
                j += 1
            blocks.append(Block("fence", body, i + 1, fence.group(2).lower(),
                                stretch is not None, grading is not None))
            i = j + 1
            continue
        if heading:
            flush()
            level, title = len(heading.group(1)), heading.group(2).strip()
            if stretch is not None and level <= stretch:
                stretch = None
            if grading is not None and level <= grading:
                grading = None
            if title.lower().startswith("stretch"):
                stretch = level
            if GRADING_HEADING.search(title):
                grading = level
            blocks.append(Block("heading", [title], i + 1, "", stretch is not None, grading is not None))
            i += 1
            continue
        if not prose:
            prose_start = i + 1
        prose.append(line)
        i += 1
    flush()
    return blocks


def prose_text(blocks):
    return " ".join(" ".join(b.text) for b in blocks if b.kind == "prose")


# --- the student's edits ----------------------------------------------------

@dataclass
class Slot:
    start: int          # first placeholder line, 0-based
    end: int            # last placeholder line
    indent: str
    arm: tuple | None   # (case line, closing brace line, label) for a hollowed case
    fill: tuple | None = None


def find_slots(lines, chapter):
    slots, i = [], 0
    while i < len(lines):
        m = PLACEHOLDER.match(lines[i])
        if not m or m.group(2) != chapter:
            i += 1
            continue
        start = end = i
        while end + 1 < len(lines) and lines[end + 1].strip().startswith("//"):
            end += 1
        if end + 1 < len(lines) and lines[end + 1].strip().startswith("throw new UnsupportedOperationException("):
            end += 1
            while not lines[end].rstrip().endswith(");"):
                end += 1
        arm = None
        case = CASE_LINE.match(lines[start - 1]) if start else None
        if case and end + 1 < len(lines) and lines[end + 1].strip() == "}":
            arm = (start - 1, end + 1, case.group(2))
        slots.append(Slot(start, end, m.group(1), arm))
        i = end + 1
    return slots


class Workfile:
    """A file the handout edits, as it was handed out plus the student's fills.

    Anchors name lines of the file as handed out, so fills are kept apart
    from it and rendered together; a command that rewrites the file (a cp)
    starts it over from what the command wrote."""

    def __init__(self, path, chapter):
        self.path, self.chapter = path, chapter
        self.baseline(path.read_text())

    def baseline(self, text):
        self.original = text.split("\n")
        self.slots = find_slots(self.original, self.chapter)
        self.rendered = text

    def sync(self):
        text = self.path.read_text() if self.path.exists() else ""
        if text != self.rendered:
            self.baseline(text)

    def write(self):
        lines = list(self.original)
        for slot in sorted((s for s in self.slots if s.fill), key=lambda s: s.fill[0], reverse=True):
            first, last, new = slot.fill
            lines[first:last + 1] = new
        self.rendered = "\n".join(lines)
        self.path.write_text(self.rendered)

    def slot_at(self, target):
        """The placeholder an anchor means: the one on its line, or the one
        inside the block that opens on it."""
        t = target - 1
        for slot in self.slots:
            if slot.start <= t <= slot.end:
                return slot
        if t >= len(self.original):
            return None
        text = "\n".join(self.original)
        offset = sum(len(line) + 1 for line in self.original[:t])
        brace = text.find("{", offset)
        if brace < 0 or ";" in text[offset:brace]:
            return None
        close = slicer.matching_brace(text, brace)
        first, last = text.count("\n", 0, brace), text.count("\n", 0, close)
        inside = [s for s in self.slots if first <= s.start <= last]
        return inside[0] if len(inside) == 1 else None

    def revised_block(self, target, code):
        """A block the handout rewrites rather than fills: the arm or method
        that opens on the anchor line, when the paste opens the same one."""
        t = target - 1
        if t >= len(self.original):
            return None
        anchored, pasted = self.original[t], code[0]
        cases = CASE_LINE.match(anchored), CASE_LINE.match(pasted)
        declarations = DECLARATION.match(anchored), DECLARATION.match(pasted)
        if not any(a and p and a.group(a.lastindex) == p.group(p.lastindex) for a, p in (cases, declarations)):
            return None
        slot = Slot(t, self.block_end(t), re.match(r"\s*", anchored).group(0), None)
        self.slots.append(slot)
        return slot

    def block_end(self, line):
        text = "\n".join(self.original)
        offset = sum(len(row) + 1 for row in self.original[:line])
        return text.count("\n", 0, slicer.matching_brace(text, text.index("{", offset)))


def snippet(lines):
    code = textwrap.dedent("\n".join(lines)).split("\n")
    while code and not code[0].strip():
        code.pop(0)
    while code and not code[-1].strip():
        code.pop()
    return code


def indented(code, indent):
    return [indent + line if line.strip() else "" for line in code]


def split_arms(code):
    arms, current = [], None
    for line in code:
        case = CASE_LINE.match(line)
        if case and not case.group(1):
            current = [line]
            arms.append((case.group(2), current))
        elif current is not None:
            current.append(line)
    return [(label, snippet(body)) for label, body in arms]


# --- following a handout ----------------------------------------------------

class Student:
    def __init__(self, name):
        self.name = name
        self.dir = STUDENTS / name
        if self.dir.exists():
            shutil.rmtree(self.dir)
        self.dir.parent.mkdir(parents=True, exist_ok=True)
        run(["git", "clone", "-q", str(ORIGIN), str(self.dir)])
        self.git("config", "user.name", "Workshop Student")
        self.git("config", "user.email", "student@example.com")
        self.git("fetch", "-q", "origin", "+refs/walk/*:refs/walk/*")
        self.cwd = self.dir

    def git(self, *args, check=True):
        return run(["git", *args], cwd=self.dir, check=check)[1].strip()

    def shell(self, script, log, timeout=TIMEOUT):
        marker = self.dir / ".git" / "walk-cwd"
        marker.unlink(missing_ok=True)
        wrapped = (f"trap 'pwd > {shlex.quote(str(marker))}' EXIT\n"
                   f"set -e\ncd {shlex.quote(str(self.cwd))}\n{script}\n")
        code, out = run(["bash", "-c", wrapped], cwd=self.dir, check=False, timeout=timeout, log=log)
        if marker.exists():
            self.cwd = Path(marker.read_text().strip())
        return code, out

    def tree(self):
        env = clean_env(GIT_INDEX_FILE=str(self.dir / ".git" / "walk-index"))
        run(["git", "add", "-A"], cwd=self.dir, env=env)
        return run(["git", "write-tree"], cwd=self.dir, env=env)[1].strip()

    def log(self, chapter, name):
        return LOGS / self.name / chapter / name


def offline(script):
    return re.sub(r"(?m)(^|[\s;&|(])\./gradlew\b", r"\1./gradlew --offline", script)


def line_found(expected, haystack):
    parts = re.split(r"\.\.\.|…|<[^<>\s]+>", " ".join(expected.split()))
    pattern = ".*?".join(re.escape(part) for part in parts)
    return re.search(pattern, " ".join(haystack.split())) is not None


def follow(student, chapter, replay=None):
    """Do what the handout says, in order. Returns the handout lines of the
    command blocks that changed the tree, which is all a second student on
    the same tree needs to repeat."""
    root = student.dir
    blocks = parse_handout((root / f"lessons/{chapter}-instructions.md").read_text())
    anchors = list(slicer.handout_anchors(chapter, root))
    works = {}
    for path in sorted((root / "src").rglob("*.java")):
        if f"// Chapter {chapter}: " in path.read_text():
            works[path] = Workfile(path, chapter)
    for _, _, _, path, _ in anchors:
        if path and path.is_file() and path not in works and path.suffix == ".java":
            works[path] = Workfile(path, chapter)
    started = time.time()
    changing, last_output, context = [], "", []
    where = lambda block: f"{chapter}-instructions.md:{block.line}"

    for block in blocks:
        if block.kind == "heading":
            context = []
            continue
        if block.kind == "prose":
            context.append(block)
            continue
        prose, context = context, []
        if block.stretch:
            continue
        if block.lang == "java":
            paste(student, chapter, block, prose, anchors, works, where(block))
        elif block.lang in SHELL:
            if replay is not None and block.line not in replay:
                continue
            script = []
            for line in block.text:
                if INTERACTIVE.match(line):
                    MANUAL.append(f"{where(block)} ({student.name}): {line.strip()}")
                else:
                    script.append(line)
            before = student.tree()
            code, out = 0, ""
            if "\n".join(script).strip():
                code, out = student.shell(offline("\n".join(script)), student.log(chapter, f"fence-{block.line}.log"))
            if len(script) < len(block.text):
                student.cwd = root
            if student.tree() != before:
                changing.append(block.line)
            for work in works.values():
                work.sync()
            unfilled = any(not s.fill for w in works.values() for s in w.slots)
            if code and not (unfilled and "gradlew" in "\n".join(script)):
                finding(chapter, student.name, "command failed",
                        f"{where(block)}: exit {code}\n" + "\n".join(script) + "\n---\n" + out[-2500:])
            last_output = out
        elif block.lang == "text" and replay is None:
            logs = [p for p in root.rglob("logs/*.log") if p.stat().st_mtime >= started]
            haystacks = [last_output] + [p.read_text(errors="replace") for p in logs]
            missing = [line for line in block.text
                       if line.strip() and not any(line_found(line, h) for h in haystacks)]
            if missing:
                finding(chapter, student.name, "expected output not seen", f"{where(block)}:\n" + "\n".join(missing))

    for path, work in works.items():
        for slot in work.slots:
            if not slot.fill:
                finding(chapter, student.name, "placeholder never filled",
                        f"{path.relative_to(root)}:{slot.start + 1}: {work.original[slot.start].strip()}")
    return changing


def paste(student, chapter, block, prose, anchors, works, where):
    code = snippet(block.text)
    if not code:
        return
    first_line, last_line = (prose[0].line, prose[-1].line + len(prose[-1].text)) if prose else (0, 0)
    near = [a for a in anchors if first_line <= a[0] < last_line]
    cue = PASTE_CUE.search(" ".join(" ".join(p.text) for p in prose))
    arms = split_arms(code) if CASE_LINE.match(code[0]) else []
    if arms and (len(arms) > 1 or not (cue and near)):
        for label, arm in arms:
            target = [(w, s) for w in works.values() for s in w.slots if s.arm and s.arm[2] == label and not s.fill]
            if not target:
                finding(chapter, student.name, "paste has nowhere to go", f"{where}: no empty `case {label}` arm")
                continue
            work, slot = target[0]
            case_line, close_line, _ = slot.arm
            slot.fill = (case_line, close_line, indented(arm, CASE_LINE.match(work.original[case_line]).group(1)))
            work.write()
        return
    if not (cue and near):
        return
    _, target, _, path, named = near[-1]
    work = works.get(path)
    slot = work.slot_at(target) or work.revised_block(target, code) if work else None
    if slot is None:
        finding(chapter, student.name, "paste has nowhere to go",
                f"{where}: line {target} of {named} is not an empty method, arm or placeholder, "
                "nor a block the paste rewrites")
        return
    if slot.fill:
        finding(chapter, student.name, "pasted twice", f"{where}: line {target} of {named} is already filled")
        return
    anchored = work.original[target - 1]
    declared = DECLARATION.match(code[0])
    if declared and DECLARATION.match(anchored) and DECLARATION.match(anchored).group(1) == declared.group(1):
        indent = re.match(r"\s*", anchored).group(0)
        slot.fill = (target - 1, work.block_end(target - 1), indented(code, indent))
    elif slot.arm and CASE_LINE.match(code[0]):
        case_line, close_line, _ = slot.arm
        slot.fill = (case_line, close_line, indented(code, CASE_LINE.match(work.original[case_line]).group(1)))
    else:
        slot.fill = (slot.start, slot.end, indented(code, slot.indent))
    work.write()


# --- checks -----------------------------------------------------------------

def test_results(root, task):
    results = {}
    for report in sorted((root / "build/test-results" / task).glob("*.xml")):
        for case in ET.parse(report).getroot().iter("testcase"):
            name = case.get("name", "").split("(")[0]
            owner = case.get("classname", "").rsplit(".", 1)[-1]
            results[f"{owner}.{name}"] = case.find("failure") is None and case.find("error") is None
    return results


def grade(student, chapter, name):
    student.shell(f"./gradlew --offline --continue chapterTest -Pchapter={chapter} test",
                  student.log(chapter, f"{name}.log"))
    return test_results(student.dir, "chapterTest"), test_results(student.dir, "test")


def check_counts(student, chapter, blocks, chapter_tests):
    total = len(chapter_tests)
    red = sum(1 for passed in chapter_tests.values() if not passed)
    said = COUNT_SENTENCE.search(" ".join(prose_text(blocks).split()))
    standard = f'"The run reports {total} tests, and {red} of them fail before you start."'
    if not said:
        finding(chapter, student.name, "no test-count sentence", f"say {standard}")
    elif (int(said.group(1)), int(said.group(2))) != (total, red):
        finding(chapter, student.name, "test count is wrong",
                f"the handout says {said.group(1)} tests with {said.group(2)} red; the branch runs {standard}")


def check_hints(student, chapter, blocks, chapter_tests):
    methods = {test.rsplit(".", 1)[-1] for test in chapter_tests}
    for block in blocks:
        if block.kind != "prose" or not block.grading or block.stretch:
            continue
        for line in block.text:
            item = HINT_ITEM.match(line)
            if not item:
                continue
            head = re.split(r" — | failing| fails", item.group(1), maxsplit=1)[0]
            for name in re.findall(r"`([^`]+)`", head):
                if not TEST_NAME.match(name) or not re.search(r"[A-Z_]", name):
                    continue
                stem = name.rstrip("*").removesuffix("...").rstrip("_")
                if not any(m == name or (stem != name and m.startswith(stem)) for m in methods):
                    finding(chapter, student.name, "failure hint names no chapter test",
                            f"`{name}` in {chapter}-instructions.md:{block.line}")


def check_branches(student, stage, documents):
    heads = {ref.rsplit("/", 1)[-1] for ref in student.git("ls-remote", "--heads", "origin").split()
             if ref.startswith("refs/heads/")}
    for document in documents:
        path = student.dir / document
        if not path.exists():
            continue
        named = {a or b for a, b in BRANCH_TOKEN.findall(path.read_text())}
        for branch in sorted(named - heads):
            finding(stage, student.name, "names a branch that does not exist", f"{document}: {branch}")


def check_links(student, chapter):
    root = student.dir
    documents = sorted((root / "lessons").glob(f"{chapter}-*.md")) + [root / "README.md"]
    for document in documents:
        text = document.read_text()
        for target in LINK.findall(text):
            if re.match(r"[a-z]+:", target):
                continue
            if not (document.parent / target).exists():
                finding(chapter, student.name, "broken link", f"{document.relative_to(root)} links to {target}")
        for token in PATH_TOKEN.findall(text):
            token = token.rstrip(".,;:)")
            if re.search(r"[*<>{}$]|logs/?$|/logs/", token):
                continue
            if not (root / token).exists():
                finding(chapter, student.name, "names a path that is not on the branch",
                        f"{document.relative_to(root)}: {token}")


def check_merge_docs(student, stage, nxt, documents):
    for document in documents if nxt else []:
        path = student.dir / document
        if path.exists() and f"git merge origin/{nxt}" not in path.read_text():
            finding(stage, student.name, "does not offer the merge path", f"{document}: no `git merge origin/{nxt}`")


def same_tree(student, chapter, tree, against, what):
    if tree == against:
        return True
    stat = student.git("diff-tree", "-r", "--stat", against, tree)
    diff = student.git("diff-tree", "-r", "-p", against, tree)
    log = student.log(chapter, f"diff-{what.replace(' ', '-')}.diff")
    log.parent.mkdir(parents=True, exist_ok=True)
    log.write_text(diff)
    finding(chapter, student.name, f"tree differs from {what}", stat + "\n" + "\n".join(diff.splitlines()[:80]))
    return False


# --- the two students -------------------------------------------------------

def walk_trunk(student):
    print("-- trunk")
    code, out = student.shell("./verification.sh", student.log("trunk", "verification.log"))
    if code:
        failed = [line.strip() for line in out.splitlines() if "FAILED" in line]
        finding("trunk", student.name, "verification.sh failed", "\n".join(failed) or out[-1500:])
    else:
        note("trunk: verification.sh passed")
    drifted = student.git("diff", "--name-only", "origin/trunk", "origin/complete", "--", *TOOLCHAIN)
    if drifted:
        finding("trunk", student.name, "trunk's toolchain is not the chapters'", drifted)
    if student.git("diff", "--stat", "origin/trunk:sylabus.md", "origin/complete:lessons/sylabus.md"):
        finding("trunk", student.name, "trunk's syllabus is not complete's", "sylabus.md vs lessons/sylabus.md")
    check_branches(student, "trunk", ["README.md", "00-setup.md", "clean-checkout.sh"])
    check_merge_docs(student, "trunk", "01-chapter", ["README.md", "00-setup.md"])


def checkout_chapter(student, chapter):
    print(f"-- {chapter} (checkout)")
    began = time.time()
    code, out = student.shell(f"./clean-checkout.sh {chapter}-chapter", student.log(chapter, "clean-checkout.log"))
    student.cwd = student.dir
    if code:
        finding(chapter, student.name, "clean-checkout.sh failed", out[-1500:])
        return None
    if student.git("rev-parse", "HEAD") != student.git("rev-parse", f"origin/{chapter}-chapter"):
        finding(chapter, student.name, "clean-checkout.sh landed elsewhere", student.git("log", "-1", "--oneline"))
    nxt = f"{int(chapter) + 1:02d}-chapter" if chapter != "07" else None
    check_branches(student, chapter, ["README.md", f"lessons/{chapter}-instructions.md", "clean-checkout.sh"])
    check_merge_docs(student, chapter, nxt, ["README.md", f"lessons/{chapter}-instructions.md"])
    check_links(student, chapter)

    chapter_tests, branch_tests = grade(student, chapter, "before")
    if not chapter_tests:
        finding(chapter, student.name, "no chapter tests ran", "see before.log")
    red = sorted(t for t, passed in chapter_tests.items() if not passed)
    leaked = sorted(t for t, passed in branch_tests.items() if not passed and t not in chapter_tests)
    if leaked:
        finding(chapter, student.name, "tests outside this chapter are red before it starts", "\n".join(leaked))
    blocks = parse_handout((student.dir / f"lessons/{chapter}-instructions.md").read_text())
    check_counts(student, chapter, blocks, chapter_tests)
    check_hints(student, chapter, blocks, chapter_tests)

    changing = follow(student, chapter)
    exact = same_tree(student, chapter, student.tree(),
                      student.git("rev-parse", f"refs/walk/{chapter}-solution^{{tree}}"), "the solution")

    chapter_after, branch_after = grade(student, chapter, "after")
    still_red = sorted(t for t, passed in {**branch_after, **chapter_after}.items() if not passed)
    if still_red or not chapter_after:
        finding(chapter, student.name, "tests red after following the handout", "\n".join(still_red) or "no results")
    harness = ""
    if int(chapter) >= 3:
        code, out = student.shell(f"./gradlew --offline -q shadowJar && python3 {slicer.HARNESS} --chapter {chapter}",
                                  student.log(chapter, "harness.log"))
        passed = len(re.findall(r"^\[PASS\]", out, re.M))
        harness = f", harness {passed} passed"
        if code:
            finding(chapter, student.name, "harness failed", "\n".join(re.findall(r"^\[FAIL\].*$", out, re.M)) or out[-1500:])
    note(f"{chapter} checkout: {len(chapter_tests)} chapter tests, {len(red)} red before, "
         f"{len(chapter_after) - len(still_red)} green after{harness}; "
         f"{'matches' if exact else 'DIFFERS FROM'} the solution ({time.time() - began:.0f}s)")
    return changing


def merge_chapter(student, chapter, replay):
    print(f"-- {chapter} (merge)")
    student.git("fetch", "-q", "origin")
    code, out = run(["git", "merge", "--no-edit", f"origin/{chapter}-chapter"], cwd=student.dir, check=False,
                    log=student.log(chapter, "merge.log"))
    if code:
        conflicted = student.git("diff", "--name-only", "--diff-filter=U", check=False)
        finding(chapter, student.name, "merge did not apply cleanly", conflicted or out[-1500:])
        student.git("merge", "--abort", check=False)
        student.git("reset", "-q", "--hard", f"origin/{chapter}-chapter")
    same_tree(student, chapter, student.tree(),
              student.git("rev-parse", f"origin/{chapter}-chapter^{{tree}}"), f"{chapter}-chapter after the merge")
    follow(student, chapter, replay=replay)
    student.git("add", "-A")
    student.git("commit", "-q", "--allow-empty", "-m", f"Finish chapter {chapter}.")
    exact = same_tree(student, chapter, student.tree(),
                      student.git("rev-parse", f"refs/walk/{chapter}-solution^{{tree}}"), "the solution")
    note(f"{chapter} merge: {'clean' if not code else 'CONFLICTED'} merge, "
         f"{'matches' if exact else 'DIFFERS FROM'} the solution")


def walk(args):
    through = CHAPTERS[: CHAPTERS.index(args.through) + 1]
    first = CHAPTERS.index(args.start)
    replay = {}
    if args.only != "merge":
        print("== checkout student")
        student = Student("checkout")
        if not args.skip_trunk and first == 0:
            walk_trunk(student)
        for chapter in through[first:]:
            replay[chapter] = checkout_chapter(student, chapter)
    if args.only != "checkout":
        print("== merge student")
        student = Student("merge")
        student.git("switch", "-q", "-c", "my-work", "origin/trunk")
        for chapter in through:
            merge_chapter(student, chapter, replay.get(chapter))
        if through[-1] == "07":
            differs = student.git("diff", "--stat", "origin/complete", "HEAD", "--", *EXACT)
            if differs:
                finding("07", student.name, "code differs from complete", differs)
            else:
                note("after 07: the merge student's code and project files are identical to complete")


def write_report():
    lines = ["# Walkthrough report", "", f"Written {time.strftime('%Y-%m-%d %H:%M:%S')}. Logs are in `{LOGS}`.", ""]
    lines += ["## Summary", ""] + [f"- {line}" for line in SUMMARY] + [""]
    lines += [f"## Findings ({len(FINDINGS)})", ""]
    for f in FINDINGS:
        lines += [f"### {f.stage} {f.student}: {f.kind}", "", "```text", f.detail.rstrip(), "```", ""]
    if MANUAL:
        lines += ["## Steps for a person", ""] + [f"- {line}" for line in dict.fromkeys(MANUAL)] + [""]
    (WORK / "report.md").write_text("\n".join(lines))
    (WORK / "report.json").write_text(json.dumps([asdict(f) for f in FINDINGS], indent=2))
    print(f"== {len(FINDINGS)} finding(s); report in {WORK / 'report.md'}")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("snapshot", help="copy this working tree into a throwaway origin")
    commands.add_parser("chain", help="publish the chapter chain into the throwaway origin")
    for name in ("walk", "all"):
        command = commands.add_parser(name, help="follow the handouts" if name == "walk" else "snapshot, chain, walk")
        command.add_argument("--through", choices=CHAPTERS, default="07", help="last chapter to walk")
        command.add_argument("--from", dest="start", choices=CHAPTERS, default="01",
                             help="first chapter the checkout student walks; the merge student always starts at trunk")
        command.add_argument("--only", choices=["checkout", "merge"], help="walk one student")
        command.add_argument("--skip-trunk", action="store_true", help="do not run verification.sh on trunk")
    args = parser.parse_args()
    WORK.mkdir(parents=True, exist_ok=True)
    stale = [LOGS] if args.command in ("chain", "all") else [LOGS / "checkout", LOGS / "merge"]
    for path in stale:
        if path.exists():
            shutil.rmtree(path)
    if args.command in ("snapshot", "all"):
        snapshot()
    if args.command in ("chain", "all"):
        chain()
    if args.command in ("walk", "all"):
        walk(args)
    write_report()
    sys.exit(1 if FINDINGS else 0)


if __name__ == "__main__":
    main()
