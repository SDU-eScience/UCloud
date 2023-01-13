package dk.sdu.cloud.utils

fun logbackConfiguration(dir: String, providerId: String, module: String) = """
<configuration>
    <appender name="im-appender" class="ch.qos.logback.core.FileAppender">
        <file>$dir/$providerId-$module.log</file>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{35} - %msg%n</pattern>
        </encoder>
    </appender>

    <logger name="dk.sdu.cloud" level="debug" additivity="false">
        <appender-ref ref="im-appender"/>
    </logger>

    <root level="error">
        <appender-ref ref="generic-error-appender"/>
    </root>
</configuration>    
"""
