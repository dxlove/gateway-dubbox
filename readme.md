# 网关系统



## 目的

我们服务框架使用的是dubbox(这个不用再多描述了），内部系统调用使用的是dubbo协议;而对于前端提供服务则使用rest协议。前端应用面临许多rest服务，为了解决调用的统一，所以需要一个网关系统。

## 为什么自己写



- 对于api网关，现在已有框架可以解决该类问题，但是基于java语言开源的没有(除了zuul,也有可能我没有找到).
- 不会C++,无法扩展nginx
- 不会C,无法扩展nginx
- 不会LUA,无法扩展ngin



## 功能设计

最初根据个人理解构建一个版本，但是设计的太复杂，并且关于网关与后端服务的心跳检测的功能没有。本次设计方案参考spring-cloud的zuul的设计思路。

## 主要功能



- 前端请求的统一拦截；


- 负载均衡；

- 心跳检测
- 鉴权
- 参数校验
- 路由


## 路由规则

很简单：前端请求的url,去掉网关的contextpath后对应的url地址即为相应的后端服务的url；
	对于后端服务的url，分两部：一部分为上下文contextpath;一部分为请求路径；
	通过监听注册中心zk,可以获取所有提供rest服务的服务器列表，根据服务的contextpath进行分组，然后进行路由请求。


## 后端服务列表获取

后端服务列表，通过监听注册中心zk，然后解析出相应的服务列表。当注册中心中注入的服务地址发生变化，则重新再次解析；
## 心跳检测
zk帮我们完成后端服务列表动态变化以及心跳检测；
## 负载均衡
主要体现在同一类型服务的后端服务器的选择上。目前只实了随机(参见RandomLoadBalanceImpl）、单jvm最小化被调用(参见LeastActiveLoadBalanceImpl)负载均衡算法；

## 示例




1. 前端请求url:http://localhost:8080/gateway/restapi/test
	


- 则网关的contextpath=gateway
	
- 后端服务的contextpath=restapi


- 根据后端服务的contextpath=restapi得到一组服务器列表，选其一，比如http://localhost:10000/,
- 则后端的服务的完整api服务地址为：
http://localhost:10000/restapi/test

- 然后调用该接口获取相应的响应，返回前端即可



## 技术栈



- springmvc
- spring
- servlet3异步
- httpclient 4.5.3
- zookeeper
- apache-chain


## 伸缩性

对于该网关系统，它所使用的web容器仍为tomcat。在实际业务部署的时候，前面仍会采用nginx作为反向代理服务器，主要是由于nginx的单机高并发能力，以及后端服务的高可用检测;

如果采用这种结构部署，则该网关系统只能手动的进行扩缩（这个需要依赖于运维人员）。那么在系统需要紧急扩缩备战中，则会遇到问题。为了解决这类问题，通过扩展一个虚拟服务，将其注册到注册中心即可。具体请参考[https://github.com/zhuzhong/gateway-ngxcfg.git](https://github.com/zhuzhong/gateway-ngxcfg.git) gateway-ngxcfg

目前这个虚拟服务注册的参数很少，可以想像nginx所需要一切参数都可以通过该服务进行初始化。通过nginx作为该网关系统的反向代理，可以通过管理平台更改相应的nginx参数，进行导流,系统下线等操作。

## 关于ZkApiInterfaceServiceImpl配置说明

		<bean id="apiInterfaceServiceImpl"
		class="com.z.gateway.service.support.ZkApiInterfaceServiceImpl" init-method="init">
		<property name="rootPath" value="${zk.rootpath}" />
		<property name="zkServers" value="${zk.zkservers}" />
	</bean>
	
	后端服务dubbo注册中心使用dubbo示例
	<dubbo:registry protocol="${dubbo.registry.protocol}" address="$		{dubbo.registry.address}" group="${dubbo.group}" check="false" />
	
	则ZkApiInterfaceServiceImpl中rootPath参数为 dubbox 注册中心zookeeper时配的group参数值；
	zk.zkservers  为address值

## 并发限制、限流

增加了ControlConcurrentFilter、RatelimitFilter两个 filter,用于并发量限制及吞吐量限流。

## 关于不显示支持上传与下载功能及解决办法
关于上传，以及获取图形验证码之类的需求，可以通过业务方法转换为对应的Base64方式去解决；而不需要网关接口去处理；
      
1. 对于上传，前端应用 将文件转换成base64 字符串，给网关，网关传给后端服务，然后后端服务自行将base64字符解析，并生成相应的文件并进行存储;
2. 获取图形验证码，即类似图形下载，文件下载之类的需求，将后台服务将其转换为相应的base64字符串，经网关返回给前端应用，前端应用自行将base64字符串进行解析，生成相应的文件或者图片等；
3. 经过这种转换的目的是，因为base64文件格式较大，如果经过原来方式更改，则将会使所有的接口长度增大，不利于优化，再者restful  格式的java上传也是有问题的。

## 重构及新增内容
### 重命名ZkApiInterfaceServiceImpl->ZkDubboRegistryReaderServiceImpl

### 增加MVC restful注册器模块
	
springmvc 对于构建restful服务越来越简单，使用者也很多。并且在组内遗留项目也出现了rest服务提供技术不单一。为了解决这一业务问题，所以增加类似springmvc 提供的rest服务注册器。它的使用方式，在springmvc实现的rest服务应用中，引入
	
		<dependency>
			<groupId>com.z.gateway</groupId>
			<artifactId>mvcrest-register</artifactId>
			<version>0.0.1-SNAPSHOT</version>
		</dependency>

	在spring中以bean的形式注入ZkMvcRestRegistryServiceImpl
	
### 增加ZkMvcRestryReaderSeviceImpl（mvc rest服务注册器的注册服务的订阅者）
在本应用内直接配置使用，配置类似ZkDubboRegistryReaderServiceImpl

### ApiServerInfoService的实现类DefaultApiInterfaceServiceImpl
	