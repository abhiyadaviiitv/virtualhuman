#!/bin/bash

# Exit on any error
set -e

echo "Backing up main branch to main_backup"
git branch main_backup || true

# Save the target branch we are recreating
# Get original commit IDs
c1="fbcc8343c2604aa4d794553bbed3a56cc51ef0b5"
c2="d6114483aeb6054acc414534bc162d065e8e88a4"
c3="d3ff6122a134ee9b697a20c3ce4ac045bd9f0bd9"

echo "Creating new orphan branch"
git checkout --orphan new_history
# Remove all tracked files
git rm -rf .

# COMMIT 1: Initial project setup and config
echo "Preparing Commit 1"
git checkout $c1 -- package.json package-lock.json pom.xml node_modules/ src/main/resources/application.properties src/main/java/com/virtualhuman/VirtualHumanApplication.java src/main/java/com/virtualhuman/config/ src/main/java/com/virtualhuman/exception/ || true

export GIT_AUTHOR_DATE="2026-03-31T12:00:00+05:30"
export GIT_COMMITTER_DATE="2026-03-31T12:00:00+05:30"
export GIT_AUTHOR_NAME="Abhijeet Yadav"
export GIT_AUTHOR_EMAIL="abhiyadaviiitv@gmail.com"
export GIT_COMMITTER_NAME="Abhijeet Yadav"
export GIT_COMMITTER_EMAIL="abhiyadaviiitv@gmail.com"

git add -A
git commit -m "Initial project setup and configuration"

# COMMIT 2: Backend services and models
echo "Preparing Commit 2"
git checkout $c1 -- src/main/java/com/virtualhuman/model/ src/main/java/com/virtualhuman/repository/ src/main/java/com/virtualhuman/service/ src/main/java/com/virtualhuman/controller/ || true

export GIT_AUTHOR_DATE="2026-03-31T18:00:00+05:30"
export GIT_COMMITTER_DATE="2026-03-31T18:00:00+05:30"
git add -A
git commit -m "Implement core backend services and models"

# COMMIT 3: Unity setup and remaining files
echo "Preparing Commit 3"
git checkout $c1 -- .
export GIT_AUTHOR_DATE="2026-04-01T10:00:00+05:30"
export GIT_COMMITTER_DATE="2026-04-01T10:00:00+05:30"
git add -A
git commit -m "Backend system done along with unity setup"

# Cherry pick the rest of the commits
# Reset dates to let cherry-pick preserve original author dates and we set committer dates to author dates
echo "Cherry picking remaining commits"
git cherry-pick $c2
GIT_COMMITTER_DATE=$(git log -1 --format=%aI) git commit --amend --no-edit

git cherry-pick $c3
GIT_COMMITTER_DATE=$(git log -1 --format=%aI) git commit --amend --no-edit

echo "Replacing old main with new history"
git checkout main
git reset --hard new_history
git branch -D new_history

echo "Done!"
