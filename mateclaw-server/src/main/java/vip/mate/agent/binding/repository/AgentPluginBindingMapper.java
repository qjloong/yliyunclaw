package vip.mate.agent.binding.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import vip.mate.agent.binding.model.AgentPluginBinding;

@Mapper
public interface AgentPluginBindingMapper extends BaseMapper<AgentPluginBinding> {
}