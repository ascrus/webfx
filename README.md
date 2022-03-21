# WebFX application
## About
WebFX is an application that allows you to organize the work of users through a thin client with your applications, 
whose interface is implemented through a web server.

The idea of the program is to allow creating a single workspace for users to work with web resources of local and 
external web servers, where the user does not need to install and configure a browser.

You describe pre-installed resource configurations for users and include them in the installation, users immediately 
receive a customized environment for working with your web portals and sites.

The program can be used as a regular browser, where the user can add their own sites and save them to favorites, 
but the JavaFX components themselves do not fully work with modern protocols, so not all web resources can work and display correctly.

In addition to working with external sources, the program can run local web servers on the user's machine,
which will automatically start when they are accessed from the program and stop when the user finishes working with them.

The program uses only open source solutions, is cross-platform and free for distribution.

## Installation
1. Download installation:
[Windows 64 version 1.0](https://easydata.ru/download/easyworkspace/EasyWebFx_windows-x64_1_0.exe);
2. Install the program in the specified directory
3. If necessary, edit in the installed directory EasyWebFx file engine.conf

## Working with favorite sites
* To add a site to favorites, open it from menu File/Open site and select from menu File/Save to favorites;
* To rename a site in favorites, open it and select  from the menu File/Rename in favorites;
* To remove a site from favorites, open it and select file/Remove from favorites from the menu.

Naming rules:
When naming a site in favorites, you need to set the Group Name/Site Name in the favorites menu 
(example: Search/Google).

Favorite sites preset:
You can describe the favorites.conf file yourself and copy it to users along with 
the installation at the place where the program is installed.

## Working with local servers
You can attach your own programs with a built-in web server to the installation, 
which must be launched when the user accesses them via localhost.

To do this, in engine.conf, write the desired configuration in the server section:
```
name_server {
    url = 'http://localhost:8080'
    command = '{SERVER_HOME}/bin/webserver'
    shutdown = 'actuator/shutdown'
    shutdown_timeout = 120
    encode = 'utf-8'
}
```
Where:
* name_server: server name
* url: server url
* command: OS command to start the server
* shutdown: web service name to stop the server
* shutdown_timeout: server stop timeout
* encode: server console code page

If the user opens a page that links to the server url, then the server is automatically started.
Until the server starts pinging on the web port, the interface will display the log console of 
its loading. As soon as it loads successfully, the html page of the server will appear 
instead of the log.

When the user closes the tab with the server page or the application itself, the server will be 
automatically stopped.

When stopping the server using a web service, it is expected that the called web service does not 
have authorization and works via POST. If the service returned an error or the server did not 
stop after the specified timeout time, then the server will be stopped forcibly as an OS process.

## Working with sources through a proxy server
In file engine.conf, uncomment the required properties into https and http sections.

## Screenshots
![Open site from favorites](https://github.com/ascrus/webfx/blob/master/screen1.png?raw=true)

![Starting a local web server](https://github.com/ascrus/webfx/blob/master/screen2.png?raw=true)

![Work with local web servers (based on Getl)](https://github.com/ascrus/webfx/blob/master/screen3.png?raw=true)

![Real-time display via sockets of the log of the called task via the local web server](https://github.com/ascrus/webfx/blob/master/screen4.png?raw=true)

![Working with external documentation systems](https://github.com/ascrus/webfx/blob/master/screen5.png?raw=true)
