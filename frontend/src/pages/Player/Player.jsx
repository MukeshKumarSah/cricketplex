import { useParams } from 'react-router-dom';
import './Player.css';

export default function Player() {
  const { id } = useParams();

  return (
    <div className="player-page">
      <h1>Player Details</h1>
      <p className="player-id">Player ID: {id}</p>
    </div>
  );
}
