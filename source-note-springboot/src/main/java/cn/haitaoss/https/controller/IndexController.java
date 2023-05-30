package cn.haitaoss.https.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-05-30 20:02
 */
@RestController
public class IndexController {
	@RequestMapping("json*")
	public Object json() {
		return "json";
	}
}
