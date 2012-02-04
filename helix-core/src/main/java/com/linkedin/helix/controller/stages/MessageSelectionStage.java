package com.linkedin.helix.controller.stages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.linkedin.helix.controller.pipeline.AbstractBaseStage;
import com.linkedin.helix.controller.pipeline.StageException;
import com.linkedin.helix.model.Message;
import com.linkedin.helix.model.ResourceGroup;
import com.linkedin.helix.model.ResourceKey;
import com.linkedin.helix.model.StateModelDefinition;

public class MessageSelectionStage extends AbstractBaseStage
{

  @Override
  public void process(ClusterEvent event) throws Exception
  {
    ClusterDataCache cache = event.getAttribute("ClusterDataCache");
    Map<String, ResourceGroup> resourceGroupMap =
        event.getAttribute(AttributeName.RESOURCE_GROUPS.toString());
    MessageGenerationOutput messageGenOutput =
        event.getAttribute(AttributeName.MESSAGES_ALL.toString());
    if (cache == null || resourceGroupMap == null || messageGenOutput == null)
    {
      throw new StageException("Missing attributes in event:" + event
          + ". Requires DataCache|RESOURCE_GROUPS|MESSAGES_ALL");
    }

    MessageSelectionStageOutput output = new MessageSelectionStageOutput();

    for (String resourceGroupName : resourceGroupMap.keySet())
    {
      ResourceGroup resourceGroup = resourceGroupMap.get(resourceGroupName);
      StateModelDefinition stateModelDef =
          cache.getStateModelDef(resourceGroup.getStateModelDefRef());
      for (ResourceKey resource : resourceGroup.getResourceKeys())
      {
        List<Message> messages =
            messageGenOutput.getMessages(resourceGroupName, resource);
        List<Message> selectedMessages = selectMessages(messages, stateModelDef);
        output.addMessages(resourceGroupName, resource, selectedMessages);
      }
    }
    event.addAttribute(AttributeName.MESSAGES_SELECTED.toString(), output);
  }

  protected List<Message> selectMessages(List<Message> messages,
                                         StateModelDefinition stateModelDef)
  {
    if (messages == null || messages.size() == 0)
    {
      return Collections.emptyList();
    }
    List<String> stateTransitionPriorityList =
        stateModelDef.getStateTransitionPriorityList();
    //todo change this and add validation logic so that state model constraints are not violated.
    if (stateTransitionPriorityList == null || stateTransitionPriorityList.isEmpty())
    {
      return messages;
    }
    Set<String> possibleTransitions = new HashSet<String>();
    for (Message message : messages)
    {
      String transition = message.getFromState() + "-" + message.getToState();
      possibleTransitions.add(transition.toUpperCase());
    }
    String preferredTransition = null;

    for (String transition : stateTransitionPriorityList)
    {
      if (possibleTransitions.contains(transition.toUpperCase()))
      {
        preferredTransition = transition;
        break;
      }
    }
    if (preferredTransition != null)
    {
      List<Message> messagesToSend = new ArrayList<Message>();
      for (Message message : messages)
      {
        String transition = message.getFromState() + "-" + message.getToState();
        if (transition.equalsIgnoreCase(preferredTransition))
        {
          messagesToSend.add(message);
        }
      }
      return messagesToSend;
    }
    return Collections.emptyList();
  }

}