resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.6.18")

addSbtPlugin("com.typesafe.sbt" % "sbt-web" % "1.4.3")
addSbtPlugin("com.typesafe.sbt" % "sbt-coffeescript" % "1.0.2")
addSbtPlugin("com.typesafe.sbt" % "sbt-less" % "1.1.2")

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.7.0")

addSbtPlugin("com.gu" % "sbt-riffraff-artifact" % "1.1.8")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.0.4")

addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "1.0.0")

libraryDependencies += "org.vafer" % "jdeb" % "1.3" artifacts Artifact("jdeb", "jar", "jar")