package vip.mate.workspace.core.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import vip.mate.workspace.core.model.WorkspacePolicyEntity;

@Mapper
public interface WorkspacePolicyMapper extends BaseMapper<WorkspacePolicyEntity> {
}
