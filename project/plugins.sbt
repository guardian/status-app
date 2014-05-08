resolvers ++= Seq(
    DefaultMavenRepository,
    Resolver.url("Play", url("http://download.playframework.org/ivy-releases/"))(Resolver.ivyStylePatterns),
    "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
    Resolver.url("sbt-plugin-releases",url("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns)
)

addSbtPlugin("com.typesafe.sbt" % "sbt-web" % "1.0.0-RC1")

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.3.0-RC1")

addSbtPlugin("com.typesafe.sbt" % "sbt-coffeescript" % "1.0.0-RC1")

addSbtPlugin("com.typesafe.sbt" % "sbt-less" % "1.0.0-RC1")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "0.6.2")

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.2.5")

