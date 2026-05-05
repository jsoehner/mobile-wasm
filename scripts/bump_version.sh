#!/bin/bash
set -e

FILE="app/build.gradle.kts"
TYPE=$1 # patch, minor, major

if [ -z "$TYPE" ]; then
    TYPE="patch"
fi

# Extract current versions
OLD_VERSION_NAME=$(grep 'versionName *=' $FILE | head -1 | sed 's/.*versionName *= *"\(.*\)".*/\1/')
OLD_VERSION_CODE=$(grep 'versionCode *=' $FILE | head -1 | sed 's/.*versionCode *= *\([0-9]*\).*/\1/')

echo "Current version: $OLD_VERSION_NAME ($OLD_VERSION_CODE)"

# Split version name
IFS='.' read -ra ADDR <<< "$OLD_VERSION_NAME"
MAJOR=${ADDR[0]}
MINOR=${ADDR[1]}
PATCH=${ADDR[2]}

if [ "$TYPE" == "major" ]; then
    MAJOR=$((MAJOR + 1))
    MINOR=0
    PATCH=0
elif [ "$TYPE" == "minor" ]; then
    MINOR=$((MINOR + 1))
    PATCH=0
else
    PATCH=$((PATCH + 1))
fi

NEW_VERSION_NAME="$MAJOR.$MINOR.$PATCH"
NEW_VERSION_CODE=$((OLD_VERSION_CODE + 1))

echo "New version: $NEW_VERSION_NAME ($NEW_VERSION_CODE)"

# Update file
sed -i.bak "s/versionName *= *\".*\"/versionName = \"$NEW_VERSION_NAME\"/" $FILE
sed -i.bak "s/versionCode *= *[0-9]*/versionCode = $NEW_VERSION_CODE/" $FILE

rm ${FILE}.bak
