package deeplink;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.mendix.core.Core;
import com.mendix.core.CoreException;
import com.mendix.m2ee.api.IMxRuntimeRequest;
import com.mendix.m2ee.api.IMxRuntimeResponse;
import com.mendix.systemwideinterfaces.core.IContext;
import com.mendix.systemwideinterfaces.core.IMendixObject;
import com.mendix.systemwideinterfaces.core.IMendixObjectMember;

import deeplink.proxies.BotStrings;
import deeplink.proxies.DeepLink;
import deeplink.proxies.SeoEntityMetadata;
import deeplink.proxies.SitemapDeeplinks;
import freemarker.core.ParseException;
import freemarker.template.Configuration;
import freemarker.template.MalformedTemplateNameException;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateNotFoundException;

public class DeeplinkSEO {
	private static final String TITLE_VARIABLE = "title";
	private static final String DESCRIPTION_VARIABLE = "description";
	private static final String KEYWORDS_VARIABLE = "keywords";
	private static final String BODY_VARIABLE = "body";

	private final String templateName;
	protected Configuration freemarkerConfiguration = new Configuration(Configuration.VERSION_2_3_27);

	public DeeplinkSEO(String templateName) {
		super();

		File resourcePath = Core.getConfiguration().getResourcesPath();
		String templatePath = String.format("%s\\%s", resourcePath.getAbsolutePath(), "deeplink\\seomodule");

		this.templateName = templateName;

		try {
			File file = new File(templatePath);
			this.freemarkerConfiguration.setDirectoryForTemplateLoading(file);
		} catch (IOException e) {
			Core.getLogger("DeeplinkSEO").error("Could not find SEO template file.", e);
		}
	}

	public static Boolean isBotRequest(IContext context, String userAgentName) throws CoreException {
		List<BotStrings> botList = BotStrings.load(context, "");

		for (BotStrings bot : botList) {
			String regex = String.format(".*%s.*", bot.getBotSearchString());

			Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
			Matcher matcher = pattern.matcher(userAgentName);

			if (matcher.find()) {
				return true;
			}
		}

		return false;
	}

	public String generateTemplateString(SeoEntityMetadata seoEntityMetadata) throws TemplateNotFoundException,
			MalformedTemplateNameException, ParseException, IOException, TemplateException {

		Template template = this.freemarkerConfiguration.getTemplate(this.templateName);

		StringWriter writer = new StringWriter();

		Map<String, Object> dataModel = new HashMap<String, Object>();

		dataModel.put(TITLE_VARIABLE, seoEntityMetadata.getTitle());
		dataModel.put(DESCRIPTION_VARIABLE, seoEntityMetadata.getDescription());
		dataModel.put(KEYWORDS_VARIABLE, seoEntityMetadata.getMetaTags());
		dataModel.put(BODY_VARIABLE, seoEntityMetadata.getBody());

		template.process(dataModel, writer);

		return writer.toString();
	}

	public String renderPage(SeoEntityMetadata seoEntityMetadata, IMendixObject displayObject) {
		try {
			if (displayObject != null) {
				List<? extends IMendixObjectMember<?>> attributes = displayObject
						.getPrimitives(Core.createSystemContext());

				Template template = createTemplate(seoEntityMetadata, attributes);

				Map<String, Object> model = this.createModel(attributes);
				return processTemplate(template, model);
			} else {
				Template template = createTemplate(seoEntityMetadata, null);
				return processTemplate(template, null);
			}
		} catch (Exception e) {
			Core.getLogger("DeeplinkSEO").error(e);
			return null;
		}
	}

	private Template createTemplate(SeoEntityMetadata seoEntityMetadata,
			List<? extends IMendixObjectMember<?>> attributes) throws Exception {
		List<String> attributeNames = this.getAttributeNames(attributes);

		String templateString = generateTemplateString(seoEntityMetadata);

		templateString = removeParametersFromTemplateThatDontMatchAttributes(templateString, attributeNames);

		Template template = new Template("template", templateString, this.freemarkerConfiguration);

		return template;
	}

	private List<String> getAttributeNames(List<? extends IMendixObjectMember<?>> attributes) {
		if (attributes != null)
			return attributes.stream().map(a -> a.getName()).collect(Collectors.toList());
		else
			return null;
	}

	private Map<String, Object> createModel(List<? extends IMendixObjectMember<?>> attributes) throws CoreException {
		Map<String, Object> model = new HashMap<String, Object>();

		for (IMendixObjectMember<?> attribute : attributes) {
			String memberName = attribute.getName();
			Object value = attribute.getValue(Core.createSystemContext());

			if (value instanceof Date) {
				value = formatDateToString((Date) value);
			}

			model.put(memberName, value);
		}

		return model;
	}

	private String processTemplate(Template template, Map<String, Object> model) throws TemplateException, IOException {
		StringWriter stringWriter = new StringWriter();

		template.process(model, stringWriter);

		return stringWriter.toString();
	}

	private Object formatDateToString(Date value) {
		String dateFormat = "yyyy-MM-dd";
		String timeFormat = "HH:mm:ss";

//		dateFormat = dateFormat != null && !dateFormat.isEmpty() ? dateFormat : "yyyy-MM-dd";
//		timeFormat = timeFormat != null && !timeFormat.isEmpty() ? timeFormat : "HH:mm:ss";

		SimpleDateFormat format = dateObjectHasNoTimeSet(value) ? new SimpleDateFormat(dateFormat)
				: new SimpleDateFormat(String.format("%s %s", dateFormat, timeFormat));

		return format.format(value);
	}

	private boolean dateObjectHasNoTimeSet(Date value) {
		Calendar calendar = new GregorianCalendar();
		calendar.setTime(value);

		return calendar.get(Calendar.HOUR_OF_DAY) == 0 && calendar.get(Calendar.MINUTE) == 0
				&& calendar.get(Calendar.SECOND) == 0;
	}

	protected String removeParametersFromTemplateThatDontMatchAttributes(String templateString,
			List<String> attributes) {
		String returnTemplate = templateString;

		String regexp = "\\$\\{(.+?)\\}";

		Matcher matcher = Pattern.compile(regexp).matcher(returnTemplate);

		while (matcher.find()) {
			String attributeInTemplate = matcher.group(1);

			if (!attributes.contains(attributeInTemplate)) {
				String regexp2 = String.format("\\$\\{(%s)\\}", attributeInTemplate);

				returnTemplate = returnTemplate.replaceAll(regexp2, " ");
			}
		}

		return returnTemplate;
	}

	// Sitemap functions

	public void serveSitemap(IContext ctx, IMxRuntimeRequest request, IMxRuntimeResponse response) throws Exception {
		Template template = this.freemarkerConfiguration.getTemplate("sitemap.fthl");

		Writer writer = response.getWriter();

		Map<String, Object> datamodel = this.getDataModel(ctx, request);

		template.process(datamodel, writer);

		response.setStatus(IMxRuntimeResponse.OK);
		response.getWriter().close();
	}

	private Map<String, Object> getDataModel(IContext ctx, IMxRuntimeRequest request) throws CoreException {
		Map<String, Object> returnMap = new HashMap<String, Object>();

		List<DeepLink> deeplinks = DeepLink.load(Core.createSystemContext(),
				"[" + DeepLink.MemberNames.SupportsSEO.toString() + "]");
		List<String> urlList = new ArrayList<String>();
		for (DeepLink link : deeplinks) {
			SeoEntityMetadata seometadata = link.getDeepLink_SeoEntityMetadata();
			if (seometadata == null)
				return null;

			String mf = seometadata.getSitemapDatasourceMicroflow();
			if ("".equals(mf))
				break;

			if (!"".equals(link.getObjectType()) && !"".equals(link.getObjectAttribute())) {
				Map<String, Object> map = new HashMap<String, Object>();
				List<IMendixObject> objects = Core.execute(ctx, mf, map);
				
				if (objects != null && objects.size() > 0) {
					for (IMendixObject obj : objects) {
						Object value = obj.getValue(ctx, link.getObjectAttribute());
						String valueStr = value.toString();
						
						String baseurl = Core.getConfiguration().getApplicationRootUrl();
						baseurl = baseurl.endsWith("/") ? baseurl : baseurl+"/";
						
						urlList.add(baseurl + deeplink.proxies.constants.Constants.getRequestHandlerName() + "/" + link.getName()
								+ "/" + valueStr);
					}
				}
			} else if (link.getUseStringArgument()) {
				List<SitemapDeeplinks> links = SitemapDeeplinks.load(ctx,
						String.format("[%s = '%s']", SitemapDeeplinks.MemberNames.DeeplinkName,
								link.getName()));

				links.stream().forEach(l -> {
					urlList.add(l.getLinkToObject());
				});
			} else {
				urlList.add(link.getArgumentExample());
			}
		}

		returnMap.put("links", urlList);

		return returnMap;
	}
}
