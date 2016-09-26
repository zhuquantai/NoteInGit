# 一 基本使用
# 1.以#开始的一行为注释，#！除外
# 2.Linux以#！开始及后面的字串确定文件类型
# 3.执行方式：
#  显式执行：bash test.sh or sh hello
#  直接执行：chmod +x test.sh  添加执行权 ./test.sh执行，由于#!/bin/bash提示系统调用
# 表明这是一个bash程序，需要由bash解释执行
#!/bin/bash

# This is comment.
# 4.echo 输出到标准输出
echo "Hello world" # Hello world
echo echo_hello # echo_hello

# 二 变量
# 赋值语句'='左端不能有空格
STR="My name is zhuquantai"
# 输出为空，大小写敏感
echo $str #
# 输出变量值
# 除了在变量赋值和在FOR循环语句头中，BASH 中的变量使用必须在变量前加"$"符号
# 由于 BASH 程序是在一个新的进程中运行的，所以该程序中的变量定义和赋值不会改变其他进程或原始 Shell 中同名变量的值，也不会影响他们的运行
echo $STR # My name is zhuquantai
# 最标准的写法
echo ${STR} # My name is zhuquantai
# 字符串连接
echo ${STR}standard # My name is zhuquantaistandard
# 不加$符号输出"STR"字串
echo STR # STR

# 整数的计算操作
# BASH 中的变量既然不需要定义，也就没有类型一说，一个变量即可以被定义为一个字符串，也可以被再定义为整数。
# 如果对该变量进行整数运算，他就被解释为整数；如果对他进行字符串操作，他就被看作为一个字符串。
x=1999
# 整形变量的算数运算需要let 或 expr
let "x=$x+1" 
echo $x  # 2000
x="olympic'"$x
echo $x # olympic'2000
x=`expr $x + 1` # 注意expr后要有空格 

# 三 局部变量


HELLO=Hello # Hello
function hello {
    local HELLO=World
    echo $HELLO # World
}
echo $HELLO # Hello
hello #在这里调用函数
echo $HELLO # Hello

# 四 整数，字串，文件的比较操作
# 参看：http://blog.csdn.net/gjq_1988/article/details/8813966?locationNum=4

