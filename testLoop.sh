#!/bin/sh

i=1;
errors=0;
while [[ i -le 100 ]] ;
do
  if [[ i -gt 1 ]] ; then
    echo " -- sleep a bit to settle down after last run (process cleanup)"
    sleep 2 
  fi 

  echo "Iteration #${i} - errors ${errors}";

  rc=$(mvn test 2>&1)
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
