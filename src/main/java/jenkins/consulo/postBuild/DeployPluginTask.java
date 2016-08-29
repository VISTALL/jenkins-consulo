package jenkins.consulo.postBuild;

import java.io.IOException;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PutMethod;
import org.kohsuke.stapler.DataBoundConstructor;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.Run;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.ListBoxModel;
import jenkins.util.VirtualFile;

/**
 * @author VISTALL
 * @since 29-Aug-16
 */
public class DeployPluginTask extends Notifier
{
	@Extension
	public static class DescriptorImpl extends BuildStepDescriptor<Publisher>
	{
		public DescriptorImpl()
		{
			load();
		}

		public String getDisplayName()
		{
			return "Deploy artifacts to plugin repository (Consulo)";
		}

		public boolean isApplicable(Class<? extends AbstractProject> jobType)
		{
			return true;
		}

		@SuppressWarnings("unused") //used by jenkins
		public ListBoxModel doFillPluginChannelItems()
		{
			ListBoxModel items = new ListBoxModel();
			for(PluginChannel goal : PluginChannel.values())
			{
				items.add(goal.name(), goal.name());
			}
			return items;
		}
	}

	private String repositoryUrl;
	private String pluginChannel;

	@DataBoundConstructor
	public DeployPluginTask(String repositoryUrl, String pluginChannel)
	{
		this.repositoryUrl = repositoryUrl;
		this.pluginChannel = pluginChannel;
	}

	public String getRepositoryUrl()
	{
		return repositoryUrl;
	}

	public String getPluginChannel()
	{
		return pluginChannel;
	}

	@Override
	public BuildStepMonitor getRequiredMonitorService()
	{
		return BuildStepMonitor.NONE;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException
	{
		Result result = build.getResult();
		if(result == null || result.isWorseThan(Result.UNSTABLE))
		{
			throw new IOException("Project is not build");
		}

		VirtualFile root = build.pickArtifactManager().root();

		List<? extends Run<?, ?>.Artifact> artifacts = build.getArtifacts();
		for(Run.Artifact artifact : artifacts)
		{
			VirtualFile child = root.child(artifact.relativePath);
			if(!child.exists())
			{
				throw new IOException(artifact.getDisplayPath() + " is not exists");
			}

			PutMethod postMethod = new PutMethod(repositoryUrl + "/deploy?channel=" + pluginChannel);
			postMethod.setRequestEntity(new InputStreamRequestEntity(child.open(), child.length(), "application/zip"));

			HttpClient client = new HttpClient();
			if(client.executeMethod(postMethod) != HttpServletResponse.SC_OK)
			{
				throw new IOException("Failed to deploy artifact " + artifact.getDisplayPath());
			}
		}

		return true;
	}
}
