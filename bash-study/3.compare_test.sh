#!/bin/bash
ASTR="abcde"
BSTR="abcdf"

echo "-------------------------"

# 这种包含'$1'写法把脚本看作一个函数，执行时需要输入参数
if [ $1 -gt 90 ];then # if then写在一行，then前面要加';'
    echo "Great"
    echo "a>b This is a error output" >& 2 # 重定向到error输出，>前面默认为1
elif [ $1 -gt 70 ];then # else if 简写为 elif
    echo "Good"
elif [ $1 -lt 60 ];then # 每一个关键符号前后都要有空格
    echo "Bad"
else
    echo "Normal"
fi

echo "-------------------------"

if [ $1 -gt 90 ] 
	then 
	echo "Good, $1" 
elif [ $1 -gt 70 ] 
	then 
	echo "OK, $1" 
else 
	echo "Bad, $1" 
fi 

