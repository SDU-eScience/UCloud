tasks {
    val dokkaHtml by getting(org.jetbrains.dokka.gradle.DokkaTask::class) {
        //outputFormat = "gfm"
        outputDirectory = "$projectDir/../wiki"
        /*
        configuration {
            includes = listOf("README.md")
        }
         */
    }
}
