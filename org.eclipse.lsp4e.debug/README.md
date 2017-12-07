# lsp4e.debug

## To setup lsp4e.debug dev environment

Clone the following repos/branches:

- git@github.com:jonahgraham/lsp4e.git -- dsp4e branch

Starting with Eclipse for Committers or similar.

Import following projects:

- lsp4e -- the root project (needed for target platform)
- org.eclipse.lsp4e.debug -- the main project

Set target platform to lsp4e/target-platforms/target-platform-oxygen/target-platform-oxygen.target

Install VSCode and the Mock Debug debug adapter. You need path to Node and to the Mock Debug in the launch configuration.

## To Run lsp4e.debug

- Launch Eclipse Application
- Create a readme.md.
- Create *ReadMe* launch configuration
