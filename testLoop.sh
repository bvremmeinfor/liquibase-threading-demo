#!/bin/sh

i=1;
errors=0;
while [[ i -le 100 ]] ;
do
  echo "Iteration #${i} - errors ${errors}";

  rc=$(mvn test 2>&1)
  ret=$?

  if [[ ret -ne 0 ]]; then
     echo "ERROR: Maven command failed (code ${ret}) - exiting"
     echo $rc
     exit 1
  fi
  
  rc2=$(echo "$rc" | grep "Tests run")

  if [[ $rc =~ "BUILD FAILURE" ]]; then
    errors=$((errors+1))
    echo "${rc}"
    echo "FAILURE: $rc2"
  else
    echo "SUCCESS: ${rc2}"
  fi

  i=$((i+1));
done;
