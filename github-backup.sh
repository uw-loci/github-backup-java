#!/bin/sh

if [ $# -eq 0 ]
  then
    echo "No arguments supplied."
    echo "Usage:"
    echo "github-backup.sh /fully/qualified/path/to/github-backup-jar-with-dependencies.jar"
  else
    echo "#!/bin/sh" > github-backup.sh
    echo "" >> github-backup.sh
    echo "java -jar $1 \$*" >> github-backup.sh
fi

