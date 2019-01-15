// This file was generated by Mendix Modeler.
//
// WARNING: Only the following code will be retained when actions are regenerated:
// - the import list
// - the code between BEGIN USER CODE and END USER CODE
// - the code between BEGIN EXTRA CODE and END EXTRA CODE
// Other code you write will be lost the next time you deploy the project.
// Special characters, e.g., é, ö, à, etc. are supported in comments.

package deeplink.actions;

import java.util.Arrays;
import java.util.List;
import com.mendix.core.Core;
import com.mendix.core.CoreException;
import com.mendix.systemwideinterfaces.core.IContext;
import com.mendix.webui.CustomJavaAction;
import deeplink.proxies.BotStrings;

/**
 * 
 */
public class PopulateBotListForFirstRun extends CustomJavaAction<Boolean>
{
	public PopulateBotListForFirstRun(IContext context)
	{
		super(context);
	}

	@Override
	public Boolean executeAction() throws Exception
	{
		// BEGIN USER CODE
		try 
		{
			this.createBotList();
			
			return true;
		}
		catch (Exception e)
		{
			Core.getLogger("DeeplinkSEO").error("Could not create bot list", e);
			return false;
		}
		// END USER CODE
	}

	/**
	 * Returns a string representation of this action
	 */
	@Override
	public String toString()
	{
		return "PopulateBotListForFirstRun";
	}

	// BEGIN EXTRA CODE
	private void createBotList() throws CoreException 
	{
		List<String> botList = this.getBotList();
		
		for (String bot : botList) 
		{
			BotStrings botStringEntity = this.createEntity(bot);
			
			botStringEntity.commit();
		}
	}
	
	private List<String> getBotList() 
	{		
		return Arrays.asList("baidu", "bingbot", "bingpreview", "msnbot", "duckduckgo", "googlebot", "teoma",
				"slurp", "yandex", "linkedinbot", "slackbot-linkexpanding", "Twitterbot", "bot");
	}
	
	public BotStrings createEntity(String botSearchString)
	{
		BotStrings botString = new BotStrings(this.getContext());
		
		botString.setBotSearchString(botSearchString);
		
		return botString;
	}
	// END EXTRA CODE
}
