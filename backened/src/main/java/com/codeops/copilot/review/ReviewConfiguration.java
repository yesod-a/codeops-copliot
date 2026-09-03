package com.codeops.copilot.review;

import com.codeops.copilot.review.git.GitCommandRunner;
import com.codeops.copilot.review.git.GitDiffParser;
import com.codeops.copilot.review.git.GitRepositoryService;
import com.codeops.copilot.review.git.ProcessBuilderGitCommandRunner;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReviewConfiguration {
    @Bean
    GitCommandRunner gitCommandRunner() {
        return new ProcessBuilderGitCommandRunner();
    }

    @Bean
    GitDiffParser gitDiffParser() {
        return new GitDiffParser();
    }

    @Bean
    GitRepositoryService gitRepositoryService(GitCommandRunner commandRunner, GitDiffParser diffParser) {
        return new GitRepositoryService(commandRunner, diffParser);
    }

    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatProtocolCustomizer() {
        return factory -> factory.setProtocol("org.apache.coyote.http11.Http11Nio2Protocol");
    }
}
