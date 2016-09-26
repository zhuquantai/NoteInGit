#!/bin/bash
# for 用来遍历集合
# 可以使用 bread continue 控制循环

# 与if类似,for do写在一行，do前面要加';'
# 空格区分元素
for day in Mon Tue Wed Thu Fri Sat Sun ; do 
    echo $day
done

echo ------------------------------

# ""包裹元素
for day in "Mon Tue Wed Thu Fri Sat Sun" ; do 
    echo $day
done
echo ------------------------------
# 如果没有in结构，将循环运行脚本时输入的参数
for param ; do
    echo $param
done


# while
COUNT=4
while [ $COUNT -gt 0 ] ; do
    echo "COUNT is: "$COUNT
    let "COUNT = $COUNT - 1"
    #COUNT=`expr $COUNT - 1`
done

echo -----------------------------
echo $COUNT
echo -----------------------------

# until
until [ $COUNT -eq 5 ] ; do
    echo "COUNT is: "$COUNT
    COUNT=`expr $COUNT + 1 `
done
