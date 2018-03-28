/*
 * Copyright 2011 Alibaba.com All right reserved. This software is the
 * confidential and proprietary information of Alibaba.com ("Confidential
 * Information"). You shall not disclose such Confidential Information and shall
 * use it only in accordance with the terms of the license agreement you entered
 * into with Alibaba.com.
 */
package com.alibaba.dubbo.governance.web.governance.module.screen;

import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.alibaba.citrus.util.StringUtil;
import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.alibaba.dubbo.registry.RegistryService;
import com.alibaba.dubbo.registry.common.domain.Provider;
import com.alibaba.dubbo.rpc.Protocol;
import com.alibaba.dubbo.rpc.ProxyFactory;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.service.EchoService;
import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.annotation.Autowired;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.utils.StringUtils;
import com.alibaba.dubbo.governance.service.ConsumerService;
import com.alibaba.dubbo.governance.service.OverrideService;
import com.alibaba.dubbo.governance.service.ProviderService;
import com.alibaba.dubbo.governance.web.common.module.screen.Restful;
import com.alibaba.dubbo.registry.common.domain.Override;
import com.alibaba.dubbo.registry.common.route.OverrideUtils;
import com.alibaba.dubbo.registry.common.util.Tool;

import javax.servlet.http.HttpServletResponse;

/**
 * Providers. URI: /services/$service/providers /addresses/$address/services /application/$application/services
 * 
 * @author ding.lid
 */
public class Services extends Restful {
    private Protocol protocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();
    private ProxyFactory proxy = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();


    @Autowired
    private ProviderService providerService;
    
    @Autowired
    private ConsumerService consumerService;
    
    @Autowired
    private OverrideService overrideService;
    @Autowired
    private RegistryService registryService;


    
    public void index(Map<String, Object> context) {
        String application = (String) context.get("application");
        String address = (String) context.get("address");
        
        if (context.get("service") == null
                && context.get("application") == null
                && context.get("address") == null) {
            context.put("service", "*");
        }
        
        List<String> providerServices = null;
        List<String> consumerServices = null;
        List<Override> overrides = null;
        if (application != null && application.length() > 0) {
            providerServices = providerService.findServicesByApplication(application);
            consumerServices = consumerService.findServicesByApplication(application);
            overrides = overrideService.findByApplication(application);
        } else if (address != null && address.length() > 0) {
            providerServices = providerService.findServicesByAddress(address);
            consumerServices = consumerService.findServicesByAddress(address);
            overrides = overrideService.findByAddress(Tool.getIP(address));
        } else {
            providerServices = providerService.findServices();
            consumerServices = consumerService.findServices();
            overrides = overrideService.findAll();
        }
        
        Set<String> services = new TreeSet<String>();
        if (providerServices != null) {
            services.addAll(providerServices);
        }
        if (consumerServices != null) {
            services.addAll(consumerServices);
        }
        
        Map<String, List<Override>> service2Overrides = new HashMap<String, List<Override>>();
        if (overrides != null && overrides.size() > 0 
                && services != null && services.size() > 0) {
            for (String s : services) {
                if (overrides != null && overrides.size() > 0) {
                    for (Override override : overrides) {
                    	List<Override> serOverrides = new ArrayList<Override>();
                    	if (override.isMatch(s, address, application)) {
                        	serOverrides.add(override);
                        }
                        Collections.sort(serOverrides, OverrideUtils.OVERRIDE_COMPARATOR);
                        service2Overrides.put(s, serOverrides);
                    }
                }
            }
        }
        
        context.put("providerServices", providerServices);
        context.put("consumerServices", consumerServices);
        context.put("services", services);
        context.put("overrides", service2Overrides);
        
        String keyword = (String) context.get("keyword");
        if (StringUtils.isNotEmpty(keyword) && ! "*".equals(keyword)) {
            keyword = keyword.toLowerCase();
            Set<String> newList = new HashSet<String>();
            Set<String> newProviders = new HashSet<String>();
            Set<String> newConsumers = new HashSet<String>();
            
            for (String o : services) {
                if (o.toLowerCase().toLowerCase().indexOf(keyword) != -1) {
                    newList.add(o);
                }
            }
            for (String o : providerServices) {
                if (o.toLowerCase().indexOf(keyword) != -1) {
                    newProviders.add(o);
                }
            }
            for (String o : consumerServices) {
                if (o.toLowerCase().indexOf(keyword) != -1) {
                    newConsumers.add(o);
                }
            }
            context.put("services", newList);
            context.put("providerServices", newProviders);
            context.put("consumerServices", newConsumers);
        }
    }

    public void echo(Map<String, Object> context) throws Exception {
        String services = (String) context.get("service");
        List<Provider> providers = providerService.findByService(services);
        List<String> unavailables = new ArrayList<String>();
        String ok = "ok";
        for(Provider provider : providers){
            Map<String, String> map = StringUtils.parseQueryString(provider.getParameters());
            map.put("service.filter","echo");
            provider.setParameters(StringUtils.toQueryString(map));
            Object obj = null;
            try {
                EchoService echo = proxy.getProxy(protocol.refer(EchoService.class, provider.toUrl()));
                obj = echo.$echo(ok);
            } catch (RpcException e) {
                unavailables.add(provider.getUrl());
                continue;
            }
            if(!obj.equals(ok)){
                unavailables.add(provider.getUrl());
            }
        }
        context.put("unavailables",unavailables);
    }

    public Boolean del(Map<String, Object> context) throws Exception {
        String services = (String) context.get("service");
        List<Provider> providers = providerService.findByService(services);
        List<Provider> unavailables = new ArrayList<Provider>();
        String ok = "ok";
        for(Provider provider : providers){
            Map<String, String> map = StringUtils.parseQueryString(provider.getParameters());
            map.put("service.filter","echo");
            provider.setParameters(StringUtils.toQueryString(map));
            Object obj = null;
            try {
                EchoService echo = proxy.getProxy(protocol.refer(EchoService.class, provider.toUrl()));
                obj = echo.$echo(ok);
            } catch (RpcException e) {
                unavailables.add(provider);
                continue;
            }
            if(!obj.equals(ok)){
                unavailables.add(provider);
            }
        }
        for(Provider provider : unavailables){
            registryService.unregister(provider.toUrl());
        }
        return true;
    }


    public boolean shield(Map<String, Object> context) throws Exception {
    	return mock(context, "force:return null");
    }

    public boolean tolerant(Map<String, Object> context) throws Exception {
    	return mock(context, "fail:return null");
    }

    public boolean recover(Map<String, Object> context) throws Exception {
    	return mock(context, "");
    }
    
    private boolean mock(Map<String, Object> context, String mock) throws Exception {
    	String services = (String) context.get("service");
    	String application = (String) context.get("application");
        if (services == null || services.length() == 0
        		|| application == null || application.length() == 0){
            context.put("message", getMessage("NoSuchOperationData"));
            return false;
        }
        for (String service : SPACE_SPLIT_PATTERN.split(services)) {
	        if (! super.currentUser.hasServicePrivilege(service)) {
	            context.put("message", getMessage("HaveNoServicePrivilege", service));
	            return false;
	        }
        }
        for (String service : SPACE_SPLIT_PATTERN.split(services)) {
	        List<Override> overrides = overrideService.findByServiceAndApplication(service, application);
	        if (overrides != null && overrides.size() > 0) {
	            for (Override override: overrides) {
	                Map<String, String> map = StringUtils.parseQueryString(override.getParams());
	                if (mock == null || mock.length() == 0) {
	                    map.remove("mock");
	                } else {
	                    map.put("mock", URL.encode(mock));
	                }
	                if (map.size() > 0) {
	                	override.setParams(StringUtils.toQueryString(map));
	                    override.setEnabled(true);
	                    override.setOperator(operator);
	                    override.setOperatorAddress(operatorAddress);
	                    overrideService.updateOverride(override);
	                } else {
	                	overrideService.deleteOverride(override.getId());
	                }
	            }
	        } else if (mock != null && mock.length() > 0) {
	            Override override = new Override();
	            override.setService(service);
	            override.setApplication(application);
	            override.setParams("mock=" + URL.encode(mock));
	            override.setEnabled(true);
	            override.setOperator(operator);
	            override.setOperatorAddress(operatorAddress);
	            overrideService.saveOverride(override);
	        }
        }
        return true;
    }

}
