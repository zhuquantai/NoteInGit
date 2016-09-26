#!bin/bash
# 函数需定义在使用前
function f1 {
    echo "This is function f1"
}
f1
# 同名函数覆盖
# 也可以加个括号
function f1() {
    echo "This is function f1_c"
}
f1

# 参数使用系统保留变量$1 $2 ...
# 返回值可以使用 return 返回一个特定的整数,如无return则返回最后一句执行结果(0或错误码)
# 通过 $? 保留变量获取函数返回值
# 传参必须使用加()的形式
function square(){
    echo $1 # $1表示传入的第一个参数,以此类推$2 $3 ...
    let "result = $1 * $1"
    echo $result
    return $result # 返回值只能为0-255,即取低八位
}
square 5
result=$? # 通过 $? 保留变量获取返回值
echo "Result: "$result
exit 0

