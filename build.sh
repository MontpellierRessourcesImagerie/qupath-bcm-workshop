#!/bin/bash

# Create build directories
mkdir -p build/exercises
mkdir -p build/lectures

# Convert index notebook if it exists
if [ -f "index.ipynb" ]; then
    jupyter nbconvert --to html "index.ipynb" --output-dir="build"
fi

# Convert all notebooks in exercises
find exercises -name "*.ipynb" -type f | while read notebook; do
    # Get relative path
    rel_path="${notebook#exercises/}"
    dir_path=$(dirname "$rel_path")
    
    # Create subdirectories if needed
    mkdir -p "build/exercises/$dir_path"
    
    # Convert notebook
    jupyter nbconvert --to html "$notebook" --output-dir="build/exercises/$dir_path"
done

# Convert all notebooks in lectures
find lectures -name "*.ipynb" -type f | while read notebook; do
    # Get relative path
    rel_path="${notebook#lectures/}"
    dir_path=$(dirname "$rel_path")
    
    # Create subdirectories if needed
    mkdir -p "build/lectures/$dir_path"
    
    # Convert notebook
    jupyter nbconvert --to html "$notebook" --output-dir="build/lectures/$dir_path"
done

echo "Build complete!"
