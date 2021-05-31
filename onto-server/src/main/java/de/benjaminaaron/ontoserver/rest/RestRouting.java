package de.benjaminaaron.ontoserver.rest;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.web.bind.annotation.RequestMethod.POST;

@RestController
public class RestRouting {

    @RequestMapping(value = "/post", method = POST)
    @ResponseBody
    public String postReq(@RequestParam Map<String, String> params) {
        System.out.println("POST request received with params: " + params);
        return "Server says hi back";
    }

}