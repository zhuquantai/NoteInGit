#!/bin/bash
# 保存了用于分割输入参数的分割字符，默认是空格
# IFS: 
echo "IFS: "$IFS
# 用户的根目录路径
# HOME: /local/user
echo "HOME: "$HOME
# 当前 Shell 的默认路径字符串（环境变量？）
#PATH: /local/tools/repo:/local/tools/scm_tools/tools/:/opt/jdk1.6.0_45/bin:/opt/jdk1.6.0_45/jre/bin:/opt/jdk1.6.0_45/lib:/opt/jdk1.6.0_45:/local/tools/adt-bundle-linux-x86_64-20130917/sdk/tools:/local/tools/adt-bundle-linux-x86_64-20130917/sdk/platform-tools:/local/tools/scm_tool/tools:/usr/lib/lightdm/lightdm:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/games:/local/tools/integrityclient/bin:/local/tools/adt-bundle-linux-x86_64-20130917/sdk/platform-tools/adb:/local/tools/android-studio/bin
echo "PATH: "$PATH
# 表示第一个系统提示符
# PS1: 
echo "PS1: "$PS1
# PS2:
# 表示第二个系统提示符 
echo "PS2: "$PS2
# 当前工作路径
# PWD: /local/user/Desktop/bash-study
echo "PWD: "$PWD
# 表示系统的默认编辑器名称
# EDITOR: 
echo "EDITOR: "$EDITOR
# 表示当前 Shell 的路径字符串
# BASH: /bin/bash
echo "BASH: "$BASH

echo ------------------------------------------------

# $0,$1,$2... :表示系统传给脚本程序或脚本程序传给函数的第0个、第一个、第二个等参数等
# $#:表示脚本程序的命令参数个数或函数的参数个数
# $?:表示脚本程序或函数的返回状态值，正常为 0，否则为非零的错误号
# $*:表示所有的脚本参数或函数参数
# $@:和 $* 含义相似，但是比 $* 更安全
# $!表示最近一个在后台运行的进程的进程号
# $RANDOM:1~65536之间的一个随机数




