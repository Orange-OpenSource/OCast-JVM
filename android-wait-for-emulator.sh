#!/usr/bin/env bash
# Originally written by Ralf Kistner <ralf@embarkmobile.com>, but placed in the public domain

set +e

bootanim=""
failcounter=0
timeout_in_sec=120

until [[ "$bootanim" =~ "stopped" ]]; do
  bootanim=`adb -e shell getprop init.svc.bootanim 2>&1 &`
  echo "$bootanim"
  if [[ "$bootanim" =~ "not found"
    || "$bootanim" =~ "device not found"
    || "$bootanim" =~ "no emulators found"
    || "$bootanim" =~ "device offline"
    || "$bootanim" =~ "running" ]]; then
    let "failcounter += 5"
    echo "Waiting for emulator to start"
    if [[ $failcounter -gt timeout_in_sec ]]; then
      echo "Timeout ($timeout_in_sec seconds) reached; failed to start emulator"
      jobs -l
      exit 1
    fi
  fi
  sleep 5
done

echo "Emulator is ready!"
exit 0
