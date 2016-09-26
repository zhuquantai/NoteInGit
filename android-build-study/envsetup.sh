# envsetup.sh的功能：建立shell命令，执行add_lunch_combo
Invoke ". build/envsetup.sh" from your shell to add the following functions to your environment:
- lunch:   lunch <product_name>-<build_variant>
- tapas:   tapas [<App1> <App2> ...] [arm|x86|mips|armv5|arm64|x86_64|mips64] [eng|userdebug|user]
- croot:   Changes directory to the top of the tree.
- m:       Makes from the top of the tree.
- mm:      Builds all of the modules in the current directory, but not their dependencies.
- mmm:     Builds all of the modules in the supplied directories, but not their dependencies.
           To limit the modules being built use the syntax: mmm dir/:target1,target2.
- mma:     Builds all of the modules in the current directory, and their dependencies.
- mmma:    Builds all of the modules in the supplied directories, and their dependencies.
- cgrep:   Greps on all local C/C++ files.
- ggrep:   Greps on all local Gradle files.
- jgrep:   Greps on all local Java files.
- resgrep: Greps on all local res/*.xml files.
- mangrep: Greps on all local AndroidManifest.xml files.
- sepgrep: Greps on all local sepolicy files.
- sgrep:   Greps on all local source files.
- godir:   Go to the directory containing a file.
# 中文解释见《深》P12

# 将第一个参数添加到LUNCH_MENU_CHOICES中，这是一个类似数组的的东西？
function add_lunch_combo()
{
    local new_combo=$1 # 定义局部变量
    local c
    for c in ${LUNCH_MENU_CHOICES[@]} ; do #遍历LUNCH_MENU_CHOICES集合 , [@]是什么意思？
        if [ "$new_combo" = "$c" ] ; then  #如果
            return
        fi
    done
    LUNCH_MENU_CHOICES=(${LUNCH_MENU_CHOICES[@]} $new_combo) # 添加
}




# test -d device
# test :  check file types and compare values
# -d FILE : FILE exists and is a directory

# find : search for files in a directory hierarchy
# -L : 
# -maxdepth : 

# unset : 删除变量或函数

# . file : 自动执行 *.sh(依靠文件头声明？)
# -L : 列出
# -maxdepth : 查找深度
# -name : 查找文件名
# sort : sort lines of text files

# Execute the contents of any vendorsetup.sh files we can find.
for f in `test -d device && find -L device -maxdepth 4 -name 'vendorsetup.sh' 2> /dev/null | sort` \
         `test -d vendor && find -L vendor -maxdepth 4 -name 'vendorsetup.sh' 2> /dev/null | sort`
do
    echo "including $f"
    . $f
done
unset f


# lunch命令的定义

function lunch()
{
	# 1.获取输入信息
    local answer
	
	# 如果第一个参数不为空，将参数放入answer局部变量中
    if [ "$1" ] ; then
        answer=$1
    else
    	# 打印 LUNCH_MENU_CHOICES
        print_lunch_menu
        echo -n "Which would you like? [aosp_arm-eng] "
        # 读取输入放入answer变量中
        read answer
    fi

	# 2. 使用输入信息初始化selection
    local selection=
	
    if [ -z "$answer" ]
    then
		# 如果answer变量为空，填入默认值aosp_arm-eng
        selection=aosp_arm-eng
    # 如果answer是一个数字,且<=LUNCH_MENU_CHOICES的条数，将相应的菜单项赋给selection
    elif (echo -n $answer | grep -q -e "^[0-9][0-9]*$")
    then
        if [ $answer -le ${#LUNCH_MENU_CHOICES[@]} ]
        then
            selection=${LUNCH_MENU_CHOICES[$(($answer-1))]}
        fi
    # 如果answer是一个包含'-'的字符串，将answer赋给selection
    elif (echo -n $answer | grep -q -e "^[^\-][^\-]*-[^\-][^\-]*$")
    then
        selection=$answer
    fi

	# 如果selection未被正确赋值，则为空，输出错误信息，返回错误码，结束。
    if [ -z "$selection" ]
    then
        echo
        echo "Invalid lunch combo: $answer"
        return 1
    fi
	
	# 3.解析selection得到product,variant
	# 定义环境变量TARGET_BUILD_APPS
    export TARGET_BUILD_APPS=
	# sed : 流编辑器,用来处理字串变量
	# 将selection中字串被'-'分割后的前半部分赋给product
    local product=$(echo -n $selection | sed -e "s/-.*$//")
    # 检查是否存在product字串对应的产品配置文件
    check_product $product
    if [ $? -ne 0 ]
    then
        echo
        echo "** Don't have a product spec for: '$product'"
        echo "** Do you have the right repo manifest?"
        # 赋空值?
        product=
    fi
    
	# 将selection中字串被'-'分割后的前半部分赋给product,其余类似
    local variant=$(echo -n $selection | sed -e "s/^[^\-]*-//")
    check_variant $variant
    if [ $? -ne 0 ]
    then
        echo
        echo "** Invalid variant: '$variant'"
        echo "** Must be one of ${VARIANT_CHOICES[@]}"
        variant=
    fi


    if [ -z "$product" -o -z "$variant" ]
    then
        echo
        return 1
    fi
	# 将product的值赋给环境变量TARGET_PRODUCT
	# 将variant的值赋给环境变量
    export TARGET_PRODUCT=$product
    export TARGET_BUILD_VARIANT=$variant
    export TARGET_BUILD_TYPE=release

    echo
    
    # 配置环境
    set_stuff_for_environment
    # 打印配置
    printconfig
}


# 执行lunch后配置好编译所需的环境变量
============================================
PLATFORM_VERSION_CODENAME=REL 平台版本名称
PLATFORM_VERSION=6.0 平台版本号
TARGET_PRODUCT=aosp_arm64
TARGET_BUILD_VARIANT=eng
TARGET_BUILD_TYPE=release
TARGET_BUILD_APPS=
TARGET_ARCH=arm64
TARGET_ARCH_VARIANT=armv8-a
TARGET_CPU_VARIANT=generic
TARGET_2ND_ARCH=arm
TARGET_2ND_ARCH_VARIANT=armv7-a-neon
TARGET_2ND_CPU_VARIANT=cortex-a15
HOST_ARCH=x86_64
HOST_OS=linux
HOST_OS_EXTRA=Linux-3.13.0-32-generic-x86_64-with-Ubuntu-12.04-precise
HOST_BUILD_TYPE=release
BUILD_ID=MRA58K
OUT_DIR=out
============================================
