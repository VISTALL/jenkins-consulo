/*
 * Copyright 2013-2016 must-be.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jenkins.consulo.postBuild;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import jenkins.consulo.postBuild.consuloArtifactTask.Generator;
import jenkins.consulo.postBuild.consuloArtifactTask.NewJRE11Generator;
import jenkins.consulo.postBuild.consuloArtifactTask.OldJRE8Generator;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

/**
 * @author VISTALL
 * @since 04-Sep-16
 */
public class ConsuloArtifactPostTask extends Notifier
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
			return "Create Consulo artifacts";
		}

		public boolean isApplicable(Class<? extends AbstractProject> jobType)
		{
			return true;
		}
	}

	private String winJre32Path;
	private String winJre64Path;
	private String linuxJre32Path;
	private String linuxJre64Path;
	private String macJre64Path;
	private boolean jre11Distribution;

	@DataBoundConstructor
	public ConsuloArtifactPostTask(String winJre32Path, String winJre64Path, String linuxJre32Path, String linuxJre64Path, String macJre64Path, boolean jre11Distribution)
	{
		this.winJre32Path = winJre32Path;
		this.winJre64Path = winJre64Path;
		this.linuxJre32Path = linuxJre32Path;
		this.linuxJre64Path = linuxJre64Path;
		this.macJre64Path = macJre64Path;
		this.jre11Distribution = jre11Distribution;
	}

	public boolean isJre11Distribution()
	{
		return jre11Distribution;
	}

	public String getWinJre32Path()
	{
		return winJre32Path;
	}

	public String getWinJre64Path()
	{
		return winJre64Path;
	}

	public String getLinuxJre32Path()
	{
		return linuxJre32Path;
	}

	public String getLinuxJre64Path()
	{
		return linuxJre64Path;
	}

	public String getMacJre64Path()
	{
		return macJre64Path;
	}

	@Override
	public BuildStepMonitor getRequiredMonitorService()
	{
		return BuildStepMonitor.BUILD;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException
	{
		Result result = build.getResult();
		if(result == null || result.isWorseThan(Result.UNSTABLE))
		{
			throw new IOException("Project is not build");
		}

		ArtifactPaths artifactPaths = ArtifactPaths.find(build);

		FilePath workspace = build.getWorkspace();

		FilePath targetDir = workspace.child(artifactPaths.getAllArtifactsPath());

		if(targetDir.exists())
		{
			targetDir.deleteContents();
		}
		else
		{
			targetDir.mkdirs();
		}

		FilePath distDir = workspace.child(artifactPaths.getRawArtifactsPath());
		if(!distDir.exists())
		{
			throw new IOException("Project is not build");
		}

		Generator generator = jre11Distribution ? new NewJRE11Generator(distDir, targetDir, build.getNumber(), listener) : new OldJRE8Generator(distDir, targetDir, build.getNumber(), listener);

		try
		{
			// win no jre
			generator.buildDistributionInArchive(artifactPaths.getWin(), null, "consulo-win-no-jre", ArchiveStreamFactory.ZIP);
			generator.buildDistributionInArchive(artifactPaths.getWin(), null, "consulo-win-no-jre", ArchiveStreamFactory.TAR);  // archive for platformDeploy
			if(generator.isSupport32Bits())
			{
				// win 32 bit
				generator.buildDistributionInArchive(artifactPaths.getWin(), winJre32Path, "consulo-win", ArchiveStreamFactory.ZIP);
				generator.buildDistributionInArchive(artifactPaths.getWin(), winJre32Path, "consulo-win", ArchiveStreamFactory.TAR); // archive for platformDeploy
			}
			// win 64 bit
			generator.buildDistributionInArchive(artifactPaths.getWin(), winJre64Path, "consulo-win64", ArchiveStreamFactory.ZIP);
			generator.buildDistributionInArchive(artifactPaths.getWin(), winJre64Path, "consulo-win64", ArchiveStreamFactory.TAR); // archive for platformDeploy

			// linux
			generator.buildDistributionInArchive(artifactPaths.getLinux(), null, "consulo-linux-no-jre", ArchiveStreamFactory.TAR);
			if(generator.isSupport32Bits())
			{
				generator.buildDistributionInArchive(artifactPaths.getLinux(), linuxJre32Path, "consulo-linux", ArchiveStreamFactory.TAR);
			}
			generator.buildDistributionInArchive(artifactPaths.getLinux(), linuxJre64Path, "consulo-linux64", ArchiveStreamFactory.TAR);

			// mac
			generator.buildDistributionInArchive(artifactPaths.getMac(), null, "consulo-mac-no-jre", ArchiveStreamFactory.TAR);
			generator.buildDistributionInArchive(artifactPaths.getMac(), macJre64Path, "consulo-mac64", ArchiveStreamFactory.TAR);
		}
		catch(Exception throwable)
		{
			throw new IOException(throwable);
		}
		return true;
	}
}
