#!/usr/bin/env bash
#
# Reset the working tree to a chapter branch exactly as published.
#
# This DELETES local work. `git clean -fdx` removes every untracked and ignored
# file, which includes your half-finished chapter, your IDE settings, and the
# build output. That is the point — it guarantees the branch you get is the
# branch everyone else has — but commit or copy anything you want to keep first.

set -euo pipefail

if [ $# -ne 1 ]; then
    echo "Usage: $0 <branch>"
    echo
    echo "Chapters:"
    echo "  $0 01-chapter"
    echo "  $0 02-chapter"
    echo "  $0 03-chapter"
    echo "  $0 04-chapter"
    echo "  $0 05-chapter"
    echo "  $0 06-chapter"
    echo "  $0 07-chapter"
    echo
    echo "Other:"
    echo "  $0 complete       # the finished server"
    echo "  $0 agent-chapter  # optional bonus chapter"
    echo
    echo "WARNING: this discards all uncommitted and untracked work."
    exit 1
fi

BRANCH=$1

# Say plainly what is about to be destroyed, and let the student back out.
# Skipped when stdin is not a terminal so the script stays usable in scripts.
if [ -t 0 ]; then
    echo "This will reset the working tree to '$BRANCH' and delete ALL untracked"
    echo "and ignored files, including any work you have not committed."
    read -r -p "Continue? [y/N] " reply
    case "$reply" in
        [yY] | [yY][eE][sS]) ;;
        *) echo "Aborted."; exit 1 ;;
    esac
fi

git fetch origin
git checkout -f -B "$BRANCH" "origin/$BRANCH"
git clean -fdx

echo "Now on '$BRANCH'."
if [[ "$BRANCH" == *-chapter && "$BRANCH" != agent-chapter ]]; then
    echo "This chapter's lesson is in lessons/. Later chapters are not on this branch."
    echo "Check your work with: ./gradlew chapterTest -Pchapter=${BRANCH%%-chapter}"
else
    echo "Lesson materials, when present, are in lessons/."
fi
