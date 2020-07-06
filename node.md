# Dubbo

## 编译过程记录

1、命令行编译

````shell script
mvn clean install -Dmaven.test.skip=true
````

2、 编译时找不到库文件

```shell script
mvn install:install-file \
  -Dfile=/home/jht/Downloads/junit-platform-launcher-1.4.0.jar \
  -DgroupId=org.junit.platform \
  -DartifactId=junit-platform-launcher \
  -Dversion=1.4.0 \
  -Dpackaging=jar
```
