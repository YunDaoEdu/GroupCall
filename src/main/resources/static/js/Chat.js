/**
 *
 */
(function () {
    let ws = new WebSocket('wss://' + location.host + '/groupcall');
    let participants = {};
    let name;


    new VConsole();


    /**
     * 接收到服务端消息时
     */
    ws.onmessage = function (message) {
        let parsedMessage = JSON.parse(message.data);

        console.warn("接收到的信息:" + parsedMessage.id);

        switch (parsedMessage.id) {
            case 'existingParticipants':
                onExistingParticipants(parsedMessage);
                break;
            case 'newParticipantArrived':
                onNewParticipant(parsedMessage);
                break;
            case 'participantLeft':
                onParticipantLeft(parsedMessage);
                break;
            case 'receiveVideoAnswer':
                receiveVideoResponse(parsedMessage);
                break;
            case 'iceCandidate':
                participants[parsedMessage.name].rtcPeer.addIceCandidate(parsedMessage.candidate, function (error) {
                    if (error) {
                        console.error("Error adding candidate: " + error);
                    }
                });
                break;
            default:
                console.error('Unrecognized message', parsedMessage);
        }
    };


    /**
     * 用户注册
     */
    function register() {
        sendMessage({id: "joinRoom"});
    }


    /**
     * 用户注册成功且该房间存在用户时（包括注册的用户自己）
     */
    function onExistingParticipants(msg) {
        let constraints = {
            audio: true,
            video: {
                mandatory: {
                    maxWidth: 320,
                    maxFrameRate: 15,
                    minFrameRate: 15
                }
            }
        };

        name = msg.name;

        let participant = new Participant(name);
        participants[name] = participant;

        console.warn("成员现况");
        console.warn(participants);

        let video = participant.getVideoElement();

        let options = {
            localVideo: video,
            mediaConstraints: constraints,
            onicecandidate: participant.onIceCandidate.bind(participant)
        };

        participant.rtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerSendonly(options,
            function (error) {
                if (error) {
                    return console.error(error);
                }
                this.generateOffer(participant.offerToReceiveVideo.bind(participant));
            });

        msg.data.forEach(receiveVideo);
    }


    /**
     * 当新成员加入时
     */
    function onNewParticipant(request) {
        receiveVideo(request.name);
    }


    /**
     * 客户端请求接收某个用户，服务端响应
     */
    function receiveVideoResponse(result) {
        participants[result.name].rtcPeer.processAnswer(result.sdpAnswer, function (error) {
            if (error) return console.error(error);
        });
    }


    /**
     * 接收某个用户的视频
     */
    function receiveVideo(sender) {

        let participant = new Participant(sender);
        participants[sender] = participant;

        console.warn("成员现况");
        console.warn(participants);

        let video = participant.getVideoElement();

        let options = {
            remoteVideo: video,
            onicecandidate: participant.onIceCandidate.bind(participant)
        };

        participant.rtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(options,
            function (error) {
                if (error) {
                    return console.error(error);
                }
                this.generateOffer(participant.offerToReceiveVideo.bind(participant));
            });
    }


    /**
     * 用户离开房间
     */
    document.getElementById("button-leave").onclick = leaveRoom;

    function leaveRoom() {
        sendMessage({
            id: 'leaveRoom'
        });

        for (let key in participants) {
            participants[key].dispose();
        }

        document.getElementById('join').style.display = 'block';
        document.getElementById('room').style.display = 'none';

        ws.close();
    }


    /**
     * 当房间有成员离开时
     */
    function onParticipantLeft(request) {
        console.log('Participant ' + request.name + ' left');
        let participant = participants[request.name];
        participant.dispose();
        delete participants[request.name];
    }


    function sendMessage(message) {
        let jsonMessage = JSON.stringify(message);
        console.log('Sending message: ' + jsonMessage);
        ws.send(jsonMessage);
    }

    $.fn.sendMessage = sendMessage;


    window.onbeforeunload = function () {
        ws.close();
    };


    /**
     * 当连接建立时，开始注册
     */
    ws.onopen = function () {
        register();
    };
})();