#!/bin/bash
# 算数运算符
# 使用let 可以使用所有运算符，但是结果需要重新赋值
# 使用expr 不能使用*，结果不需要赋值
# + - * / %(加减乘除余)
a=7
b=3
echo "a+b: "`expr $a + $b`
let "c=$a + $b"
echo "a+b: "$c

echo "a-b: "`expr $a - $b`
let "c=$a - $b"
echo "a-b: "$c 

echo "a/b: "`expr $a / $b`
let "c=$a / $b"
echo "a/b: "$c
echo "a%b: "`expr $a % $b`
let "c=$a % $b"
echo "a%b: "$c
# += -= *= /=
# << <<= >> >>=(位移操作)
# & &= | |=(与或操作)
# ~ !(非)
# ^ ^=(异或)
# < > <= >= == !=(比较操作)
# && ||(逻辑与或)

# 变量的特殊操作
# ${var-default}表示如果变量 $var 还没有设置，则保持 $var 没有设置的状态，并返回后面的默认值 default,否则返回var
echo ${var-1}
# ${var=default}表示如果变量 $var 还没有设置，则设置var并取默认值 default,否则返回var
echo ${var=2}
# ${var?default}表示如果变量 $var 已经设置，则返回该变量的值，否则将后面的 err_msg 输出到标准错误输出上。
# calculate_test.sh: line 31: var: There is an error.
echo ${var?There is an error.}

# ${var+otherwise}表示如果变量 $var 已经设置，则返回 otherwise 的值，否则返回空( null )，可以用于检验变量是否存在
echo ${var+3}

# 其他对变量处理:
# ${var#pattern}, ${var##pattern} 用于从变量 $var 中剥去最短（最长）的和 pattern 相匹配的最左侧的串。
# ${var%pattern}, ${var%%pattern} 用于从变量 $var 中剥去最短（最长）的和 pattern 相匹配的最右侧的串
# ${var:pos} 表示去掉变量 $var 中前 pos 个字符。
# ${var:pos:len} 表示变量 $var 中去掉前 pos 个字符后的剩余字符串的前 len 个字符。
# ${var/pattern/replacement} 表示将变量 $var 中第一个出现的 pattern 模式替换为 replacement 字符串。
# ${var//pattern/replacement} 表示将变量 $var 中出现的所有 pattern 模式全部都替换为 replacment 字符串
