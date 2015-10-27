var mongo = require('mongodb').MongoClient,
	client = require('socket.io').listen('8080').sockets;

mongo.connect('mongodb://127.0.0.1/chat',function(err,db){
	if(err) throw err;
	
	client.on('connection',function(socket){
		var col = db.collection('messages');
		
		var sendStatus = function(s){
			socket.emit('status', s);
		};
		
		// emit all message when initally connect
		col.find().limit(100).sort({_id: 1}).toArray(function(err,res){
			if(err) throw err;
			socket.emit('output',res);
		});
		
		// wait for input
		socket.on('input', function(data){
			var name = data.name;
			var message = data.message;
			var whitespacePattern = /^\s*$/;
			if(whitespacePattern.test(name)||whitespacePattern.test(message)){
				sendStatus('name and message is required');
			}else{
				col.insert({name: name, message: message}, function(){

					// emit latest message as array
					client.emit('output',[data]);
					// emit the status
					sendStatus({
						message : 'message sent',
						clear : true
					});
				});
			}
		  
		});
	});
});


