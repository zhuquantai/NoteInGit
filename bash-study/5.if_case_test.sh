#!/bin/bash
# if
ASTR="abcde"
BSTR="abcdf"

echo "-------------------------"


if [ $1 -gt 90 ];then # if then写在一行要加';'
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

# case
echo "Hit a key, then hit return." 
read Keypress # 读键盘的一个输入

case "$Keypress" in 
[a-z] ) echo "Lowercase letter";; # 这里[a-z]使用正则表达式？ 
[A-Z] ) echo "Uppercase letter";; 
[0-9] ) echo "Digit";; 
* ) echo "Punctuation, whitespace, or other";; 
esac

echo --------------------------

echo "Please input another key ,then hit return."
read Key
case "$Key" in
a ) echo "A";;
b ) echo "B";;
1 ) echo "number:"$Key;;
* ) echo "O";;
esac

