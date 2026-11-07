package uz.ttpu.inventory.workflow.service;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import uz.ttpu.inventory.workflow.api.WorkflowDefinitionDto;
import uz.ttpu.inventory.workflow.api.WorkflowInstanceDto;
import uz.ttpu.inventory.workflow.api.WorkflowStepDecisionDto;
import uz.ttpu.inventory.workflow.api.WorkflowStepDefinitionDto;
import uz.ttpu.inventory.workflow.domain.WorkflowDefinition;
import uz.ttpu.inventory.workflow.domain.WorkflowInstance;
import uz.ttpu.inventory.workflow.domain.WorkflowStepDecision;
import uz.ttpu.inventory.workflow.domain.WorkflowStepDefinition;

@Mapper(componentModel = "spring")
public interface WorkflowMapper {

    WorkflowDefinitionDto toDefinitionDto(WorkflowDefinition definition);

    WorkflowStepDefinitionDto toStepDefinitionDto(WorkflowStepDefinition stepDefinition);

    @Mapping(target = "definitionId", source = "definition.id")
    @Mapping(target = "definitionCode", source = "definition.code")
    WorkflowInstanceDto toInstanceDto(WorkflowInstance instance);

    WorkflowStepDecisionDto toDecisionDto(WorkflowStepDecision decision);
}
