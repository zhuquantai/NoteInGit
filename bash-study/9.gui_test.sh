#!bin/bash
OPTIONS="Hello Quit" # 默认使用空格$IFS分割
select opt in $OPTIONS; do # 使用select in语句
    if [ "$opt" = "Quit" ]; then 
        echo done 
        exit # 退出
    elif [ "$opt" = "Hello" ]; then 
        echo Hello World 
    else 
        clear # 清屏
        echo bad option 
    fi 
done

