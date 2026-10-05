## kadx analysis

Various utilities for analyze and process code and related information.


### Call graph

Full app code usage/call graph.
Usage:
```java
KadxArgs args = new KadxArgs();
args.addInputFile(new File("input.apk"));
try (KadxDecompiler kadx = new KadxDecompiler(args)) {
  kadx.load();

  ICallGraph callGraph = KadxCallGraph.builder(kadx)
    .includePackages("com.example") // filter nodes by package
    .resolvedOnly(true) // add nodes only from app (exclude framework/lib calls)
    .build();

  for (ICallGraphEdge edge : callGraph.edges()) {
    if (edge.isResolved()) {
      System.out.printf("Edge from '%s' to '%s'%n", edge.from(), edge.to());
    }
  }
  callGraph.writeDot(Path.of("test.dot")); // export to '.dot'
  callGraph.writeJson(Path.of("test.json")); // export to JSON
}
```
