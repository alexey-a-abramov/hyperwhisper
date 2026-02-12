#!/data/data/com.termux/files/usr/bin/bash

# Create temporary directory if it doesn't exist
mkdir -p $HOME/tmp

# Start proot with proper bindings
# -0: fake root (uid 0)
# -b $HOME/tmp:/tmp: bind Termux tmp to /tmp
# -b $HOME:/root: bind Termux home to /root
proot  \
  -b $HOME/tmp:/tmp \
  -b $HOME:/root \
  -w /root/projects/hyperwhisper \
  bash -c '
    # Create Claude Code temp directories
    mkdir -p /tmp/claude
    
    # Verify we are in the right place
    echo "Current directory: $(pwd)"
    ls -la gradlew 2>/dev/null && echo "✓ Gradle wrapper found" || echo "✗ Gradle wrapper not found"
    
    # Start Claude Code
    exec claude
  '
