#!/bin/bash

# Script to verify that the MCP workshop environment is properly set up

echo "MCP Workshop Verification Script"
echo "========================="
echo ""

# Step 1: Verify JDK is installed and version is at least 21
echo "Step 1: Verifying JDK installation..."
echo "------------------------------------"
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
    echo "Java version detected: $(java -version 2>&1 | head -n 1)"
    
    # Check if version is numeric and at least 21
    if [[ "$JAVA_VERSION" =~ ^[0-9]+$ ]] && [ "$JAVA_VERSION" -ge 21 ]; then
        echo ""
        echo "✅ Step 1 PASSED: JDK $JAVA_VERSION is installed (meets minimum requirement of 21)"
        JDK_EXIT_CODE=0
    else
        echo ""
        echo "❌ Step 1 FAILED: JDK version $JAVA_VERSION is below the minimum requirement of 21"
        JDK_EXIT_CODE=1
    fi
else
    echo ""
    echo "❌ Step 1 FAILED: Java is not installed or not in PATH"
    JDK_EXIT_CODE=1
fi

echo ""

# Step 2: Verify Gradle wrapper version
echo "Step 2: Verifying Gradle wrapper..."
echo "-----------------------------------"
./gradlew --version
VERSION_EXIT_CODE=$?

if [ $VERSION_EXIT_CODE -eq 0 ]; then
    echo ""
    echo "✅ Step 2 PASSED: ./gradlew --version completed successfully!"
else
    echo ""
    echo "❌ Step 2 FAILED: ./gradlew --version failed with exit code $VERSION_EXIT_CODE"
fi

echo ""

# Step 3: Verify Gradle project can run clean
echo "Step 3: Verifying Gradle project build..."
echo "----------------------------------------"
./gradlew clean
CLEAN_EXIT_CODE=$?

if [ $CLEAN_EXIT_CODE -eq 0 ]; then
    echo ""
    echo "✅ Step 3 PASSED: ./gradlew clean completed successfully!"
else
    echo ""
    echo "❌ Step 3 FAILED: ./gradlew clean failed with exit code $CLEAN_EXIT_CODE"
fi

echo ""

# Step 4: Verify Node.js, NPM, and npx are installed with minimum versions
echo "Step 4: Verifying Node.js, NPM, and npx installation..."
echo "-------------------------------------------------------"
NODE_EXIT_CODE=0

# Check Node.js (minimum version 22)
if command -v node &> /dev/null; then
    NODE_VERSION=$(node --version | sed 's/v//')
    NODE_MAJOR=$(echo $NODE_VERSION | cut -d'.' -f1)
    echo "Node.js version detected: v$NODE_VERSION"
    
    if [ "$NODE_MAJOR" -ge 22 ]; then
        echo "✓ Node.js version meets minimum requirement (v22)"
    else
        echo "❌ Node.js version $NODE_VERSION is below minimum requirement of v22"
        NODE_EXIT_CODE=1
    fi
else
    echo "❌ Node.js is not installed or not in PATH"
    NODE_EXIT_CODE=1
fi

# Check NPM (minimum version 10.9)
if command -v npm &> /dev/null; then
    NPM_VERSION=$(npm --version)
    NPM_MAJOR=$(echo $NPM_VERSION | cut -d'.' -f1)
    NPM_MINOR=$(echo $NPM_VERSION | cut -d'.' -f2)
    echo "NPM version detected: $NPM_VERSION"
    
    # Check if version is 10.9 or higher
    if [ "$NPM_MAJOR" -gt 10 ] || ([ "$NPM_MAJOR" -eq 10 ] && [ "$NPM_MINOR" -ge 9 ]); then
        echo "✓ NPM version meets minimum requirement (10.9)"
    else
        echo "❌ NPM version $NPM_VERSION is below minimum requirement of 10.9"
        NODE_EXIT_CODE=1
    fi
else
    echo "❌ NPM is not installed or not in PATH"
    NODE_EXIT_CODE=1
fi

# Check npx (minimum version 10.9)
if command -v npx &> /dev/null; then
    NPX_VERSION=$(npx --version)
    NPX_MAJOR=$(echo $NPX_VERSION | cut -d'.' -f1)
    NPX_MINOR=$(echo $NPX_VERSION | cut -d'.' -f2)
    echo "npx version detected: $NPX_VERSION"
    
    # Check if version is 10.9 or higher
    if [ "$NPX_MAJOR" -gt 10 ] || ([ "$NPX_MAJOR" -eq 10 ] && [ "$NPX_MINOR" -ge 9 ]); then
        echo "✓ npx version meets minimum requirement (10.9)"
    else
        echo "❌ npx version $NPX_VERSION is below minimum requirement of 10.9"
        NODE_EXIT_CODE=1
    fi
else
    echo "❌ npx is not installed or not in PATH"
    NODE_EXIT_CODE=1
fi

if [ $NODE_EXIT_CODE -eq 0 ]; then
    echo ""
    echo "✅ Step 4 PASSED: Node.js (v22+), NPM (10.9+), and npx (10.9+) meet all requirements!"
else
    echo ""
    echo "❌ Step 4 FAILED: One or more Node.js tools are missing or below minimum version"
fi

echo ""


# Step 5: Verify MCP Inspector can run
echo "Step 5: Verifying MCP Inspector..."
echo "----------------------------------"
echo "Fetching and running the MCP Inspector to verify npx functionality..."
echo "(The first run downloads the package, which can take a minute)"
echo ""

MCP_EXIT_CODE=0

# Run the inspector's --help instead of starting a server: it proves npx can fetch
# and execute the package without binding ports that may already be in use.
if npx --yes @modelcontextprotocol/inspector@2.5.0 --help > mcp_output.log 2>&1; then
    echo ""
    echo "✅ Step 5 PASSED: MCP Inspector ran successfully!"
    echo "npx can fetch and run MCP tools correctly."
    MCP_EXIT_CODE=0
else
    echo ""
    echo "❌ Step 5 FAILED: MCP Inspector did not run correctly"
    if [ -f mcp_output.log ]; then
        echo "Output received:"
        head -n 10 mcp_output.log
    fi
    MCP_EXIT_CODE=1
fi

# Clean up
rm -f mcp_output.log 2>/dev/null

echo ""
echo "Summary"
echo "======="

# Final summary
if [ $JDK_EXIT_CODE -eq 0 ] && [ $VERSION_EXIT_CODE -eq 0 ] && [ $CLEAN_EXIT_CODE -eq 0 ] && [ $NODE_EXIT_CODE -eq 0 ] && [ $MCP_EXIT_CODE -eq 0 ]; then
    echo "✅ ALL TESTS PASSED: All development tools are properly configured!"
    echo "  - JDK 21+ ✓"
    echo "  - Gradle wrapper ✓"
    echo "  - Gradle project build ✓"
    echo "  - Node.js, NPM, and npx ✓"
    echo "  - MCP Inspector ✓"
    exit 0
else
    echo "❌ VERIFICATION FAILED: One or more steps failed. Please check the errors above."
    exit 1
fi