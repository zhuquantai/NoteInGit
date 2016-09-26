# 这是注释
# 一 编译规则
# 1.如果这个工程没有编译过，那么我们的所有C文件都要编译并被链接。
# 2.如果这个工程的某几个C文件被修改，那么我们只编译被修改的C文件，并链接目标程序。
# 3.如果这个工程的头文件被改变了，那么我们需要编译引用了这几个头文件的C文件，并链接目标程序。

# 二 依赖性
# 1.make会在当前目录下找名字叫“Makefile”或“makefile”的文件。
# 2.如果找到，它会找文件中的"第一个目标文件（target）"，作为最终的目标文件。如实例中的main
# 3.如果main文件不存在，或是mian所依赖的后面的.o 文件的文件修改时间要比edit这个文件新，那么，他就会执行后面所定义的命令来生成edit这个文件。
# 4.如果main所依赖的.o文件也不存在，那么make会在当前文件中找目标为.o文件的依赖性，如果找到则再根据那一个规则生成.o文件。（这有点像一个堆栈的过程）（有点像一个树）
# 5.当然，你的C文件和H文件是存在的啦，于是make会生成 .o 文件，然后再用 .o 文件声明make的终极任务，也就是执行文件main了。

# 三 代码
# makefile三个常用变量:$@(目标文件),$^(所有依赖文件),$<(第一个依赖文件)
# gcc -c :只编译不链接
# gcc -o :输出可执行文件 

# 描述文件的依赖关系说明
# 描述格式：
# target: components
# [TAB]command(shell),这里注意一定要使用TAB键，不能用空格
# components中如果有一个以上的文件比target文件要新的话，command所定义的命令就会被执行

# Vervion1:了解原理
#main: main.o mytool1.o mytool2.o
	#gcc -o main main.o mytool1.o mytool2.o
#mian.o: main.c mytool1.h mytool2.h
	#gcc -c main.c
#mytool1.o: mytool1.c mytool1.h
	#gcc -c mytool1.c
#mytool2.o: mytool2.c mytool2.h
	#gcc -c mytool2.c

# Version2:使用精简变量,$@,$^,$<	
#main: main.o mytool1.o mytool2.o
	#gcc -o $@ $^

#mian.o: main.c mytool1.h mytool2.h
	#gcc -c $<
#mytool1.o: mytool1.c mytool1.h
	#gcc -c $<
#mytool2.o: mytool2.c mytool2.h
	#gcc -c $<
	
# Version3:最终精简版
main: main.o mytool1.o mytool2.o
	gcc -o $@ $^
..c.o:
	gcc -c $<
# 命令前加上@，在调用时不显示命令
clean:
	@rm main *.o
# 如果第二条命令需在第一条命令到基础上执行，需要写在一行中
pwd:
	@cd ~ ; pwd
	

