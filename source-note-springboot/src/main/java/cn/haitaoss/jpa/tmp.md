![Snipaste_2023-12-11_18-43-17](./.tmp_imgs/Snipaste_2023-12-11_18-43-17.png)

看了 spring boot jpa 的自动装配发现原来的写法是

![image-20231211184545439](./.tmp_imgs/image-20231211184545439.png)

答案很明显了我们应该从 yml 获取配置信息设置到 LocalContainerEntityManagerFactoryBean 才对，所以这么改就可以让 配置在 yml 中的jpa配置生效了

![image-20231211184743363](./.tmp_imgs/image-20231211184743363.png)