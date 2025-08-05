function createTopic(){
    alert("Creating Topic");
}

function createConnection(){

}

function getConnections(){
    // todo: dummy connection
    const sessionId = getCookie("SESSION");

    const connections = [{
        name: "connection-1",
        host: "192.168.2.139",
        port: "9920",
      },{
                name: "connection-2",
                host: "192.168.2.122",
                port: "9920",
              }];
    return connections;
}

function getCookie(name) {
  const cookies = document.cookie.split(';');
  for (let i = 0; i < cookies.length; i++) {
    let cookie = cookies[i].trim();
    if (cookie.startsWith(name + '=')) {
      return cookie.substring(name.length + 1);
    }
  }
  return null;
}