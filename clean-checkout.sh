if [ $# -ne 1 ]; then
    echo "Usage: $0 <branch>"
    echo
    echo "Examples:"
    echo "  $0 complete"
    echo "  $0 01-chapter"
    echo "  $0 02-chapter"
    echo "  $0 03-chapter"
    echo "  $0 04-chapter"
    echo "  $0 05-chapter"
    echo "  $0 06-chapter"

    exit 1

fi

BRANCH=$1

git fetch origin
git checkout -f -B $BRANCH origin/$BRANCH
git clean -fdx