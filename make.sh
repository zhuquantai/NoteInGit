#! /bin/bash
function opera_time(){
    start_time=$1
    end_time=$2
    diff=$(($(date +%s -d "$end_time") - $(date +%s -d "$start_time")))
    usetime=$(($diff / 60))
}

function show()
{
    set +x
    echo -e "\033[32;1m==========================\033[0m"
    echo -e "\033[34;1m[INFO]$1\033[0m"
    echo -e "\033[32;1m==========================\033[0m"
    set -x
}
export PATH=/local/repo:$PATH
download_comm="repo init -u git@10.92.32.10:alps/manifest -m mtk6763-p-venice-v2.0-dint.xml"
build_comm="./tclMake -o=TARGET_BUILD_VARIANT=userdebug k63v1us_64_bsp new"
project="venice"

for((i=2;i<5;i++))
do
    if [ -d /local/$project ];then
	rm -rf /local/$project
    fi
    mkdir -p /local/$project
    start_time=$(date +"%Y%m%d %H:%M:%S")
    l_start_time=$(date +"%H:%M:%S")
    show "download start : $start_time"
    cd /local/$project && $download_comm && repo sync -j$[2**$i]
    echo "cd /local/$project && $download_comm -j$[2**$i]" >> ~/result.txt
    end_time=$(date +"%Y%m%d %H:%M:%S")
    l_end_time=$(date +"%H:%M:%S")
    show "download end : $end_time"
    opera_time $l_start_time $l_end_time
    show "download use : ${usetime} minutes"
    echo "download code use $[2**$i] thead start_time=$start_time,end_time=$end_time,use_time=${usetime} minutes" >> ~/result.txt
    echo " " >> ~/result.txt
    echo "=======" >> ~/result.txt
done

for((i=2;i<5;i++))
do
    cd /local/$project
    if [ -d /local/$project/out ];then
       rm -rf /local/$project/out
    fi
    start_time=$(date +"%Y%m%d %H:%M:%S")
    l_start_time=$(date +"%H:%M:%S")
    show "build start : $start_time"
    cd ~/$project && $build_comm && make -j$[2**$i] 2>&1 | tee build.log
    echo "cd /local/$project && $build_comm && make -j$[2**$i] 2>&1 | tee build.log" >> ~/result.txt
    end_time=$(date +"%Y%m%d %H:%M:%S")
    l_end_time=$(date +"%H:%M:%S")
    show "build end : $end_time"
    opera_time $l_start_time $l_end_time
    show "build use : ${usetime} minutes"
    echo "build code use $[2**$i] thead start_time=$start_time,end_time=$end_time,use_time=${usetime} minutes" >> ~/result.txt
    echo " " >> ~/result.txt
    echo "=======" >> ~/result.txt
done


